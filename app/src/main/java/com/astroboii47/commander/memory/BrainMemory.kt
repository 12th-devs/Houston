package com.astroboii47.commander.memory

import android.content.Context
import com.astroboii47.commander.cosineSim
import com.astroboii47.commander.embedText
import com.astroboii47.commander.secondbrain.db.BrainFact
import com.astroboii47.commander.secondbrain.db.BrainFactFts
import com.astroboii47.commander.secondbrain.db.FactKind
import com.astroboii47.commander.secondbrain.db.SecondBrainDb
import com.astroboii47.commander.secondbrain.db.Verification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Cosine threshold above which two same-kind facts are considered duplicates. */
const val DEDUP_COSINE = 0.92

data class ContradictionFlag(val existing: BrainFact, val reason: String)

data class ConsolidationReport(
    val merged: Int,
    val expired: Int,
    val promoted: Int,
    val notes: List<String>,
)

private fun csvOf(vec: List<Float>): String =
    vec.joinToString(",") { "%.4f".format(it) }

private fun vecOf(csv: String): List<Float> =
    if (csv.isBlank()) emptyList()
    else csv.split(",").mapNotNull { it.toFloatOrNull() }

/**
 * Persistent memory over Room. Rules:
 * - Only user confirmation (or explicit import) raises verification; the
 *   agent may only ever write INFERRED or TEMPORARY.
 * - Near-duplicates merge instead of multiplying; nothing is silently
 *   deleted — superseded rows stay in the table with a pointer.
 * - Contradiction detection is heuristic and always labeled as such.
 */
class BrainMemory(private val db: SecondBrainDb) {
    private fun dao() = db.facts()

    suspend fun liveFacts(limit: Int = 100): List<BrainFact> =
        withContext(Dispatchers.IO) { dao().recent(limit) }

    suspend fun byKind(kind: String, limit: Int = 100): List<BrainFact> =
        withContext(Dispatchers.IO) { dao().byKind(kind, limit) }

    /**
     * Stores a candidate memory. Returns the stored row (possibly an
     * existing duplicate that absorbed the candidate).
     */
    suspend fun remember(
        kind: String,
        text: String,
        sourceRef: String,
        sourceKind: String = "manual",
        verification: String = Verification.INFERRED,
        confidence: Double = 0.5,
        expiresAt: Long? = null,
    ): BrainFact = withContext(Dispatchers.IO) {
        val clean = text.trim().take(2000)
        require(clean.length >= 3) { "Memory text is too short." }
        require(kind in setOf(FactKind.FACT, FactKind.GOAL, FactKind.EXPERIENCE, FactKind.PROJECT, FactKind.TEMPORARY)) {
            "Unknown fact kind."
        }
        val vec = embedText(clean) ?: emptyList()
        val now = System.currentTimeMillis()
        val existing = dao().allLive().filter { it.kind == kind }
        if (vec.isNotEmpty()) {
            for (e in existing) {
                val ev = vecOf(e.embedding)
                if (ev.size == vec.size && cosineSim(vec, ev) >= DEDUP_COSINE) {
                    // Duplicate: keep the stronger verification, refresh timestamp.
                    val keepVerification = strongerOf(e.verification, verification)
                    val merged = e.copy(
                        text = if (e.text.length >= clean.length) e.text else clean,
                        confidence = maxOf(e.confidence, confidence).coerceIn(0.0, 1.0),
                        verification = keepVerification,
                        updatedAt = now,
                    )
                    dao().upsert(merged)
                    syncFts(merged.id, merged.text, merged.kind)
                    return@withContext merged
                }
            }
        }
        val fact = BrainFact(
            id = "fact-${UUID.randomUUID()}-${now.toString(36)}",
            kind = kind,
            text = clean,
            confidence = confidence.coerceIn(0.0, 1.0),
            verification = verification,
            sourceRef = sourceRef.take(300),
            sourceKind = sourceKind.take(60),
            embedding = if (vec.isEmpty()) "" else csvOf(vec),
            createdAt = now,
            updatedAt = now,
            expiresAt = expiresAt,
        )
        dao().upsert(fact)
        syncFts(fact.id, fact.text, fact.kind)
        fact
    }

    private suspend fun syncFts(factId: String, text: String, kind: String) {
        dao().deleteFts(factId)
        if (text.isNotBlank()) {
            dao().insertFts(BrainFactFts(factId = factId, text = text.take(4000), kind = kind))
        }
    }

    suspend fun recall(query: String, limit: Int = 8): List<BrainFact> =
        withContext(Dispatchers.IO) {
            val vec = embedText(query) ?: return@withContext emptyList()
            val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
            // FTS candidate ids first (falls back to empty on any failure),
            // then the same literal+semantic ranking over the union.
            val ftsIds = runCatching {
                val q = sanitizeFtsQuery(query)
                if (q.isBlank()) emptyList() else dao().matchIds(q, 40)
            }.getOrDefault(emptyList())
            val ftsHits = if (ftsIds.isEmpty()) emptyMap<String, BrainFact>()
            else runCatching { dao().byIds(ftsIds).filter { it.supersededBy == null }.associateBy { it.id } }
                .getOrDefault(emptyMap())
            dao().allLive().mapNotNull { f ->
                val ev = vecOf(f.embedding)
                if (ev.size != vec.size) return@mapNotNull null
                val cos = (cosineSim(vec, ev) + 1.0) / 2.0
                val literal = if (terms.isEmpty()) 0.0
                else terms.count { it in f.text.lowercase() }.toDouble() / terms.size
                val score = 0.65 * cos + 0.35 * literal
                // Trusted memories rank slightly higher, all else equal.
                val boost = if (f.verification == Verification.USER_CONFIRMED) 0.05 else 0.0
                // FTS full-text hit: small, capped bonus so exact-term matches
                // surface without overriding semantic + trust ordering.
                val fts = if (f.id in ftsHits) 0.03 else 0.0
                if (score + boost + fts < 0.40) null else f to (score + boost + fts)
            }.sortedByDescending { it.second }.take(limit).map { it.first }
        }

    /**
     * Heuristic contradiction flags: shared subject (≥3 content tokens) plus
     * a differing number/date or an explicit negation flip. Always labeled
     * heuristic — the user resolves, the agent never auto-deletes.
     */
    fun flagContradictions(newText: String, existing: List<BrainFact>): List<ContradictionFlag> =
        flagCheck(newText, existing)

    suspend fun reviewCandidates(limit: Int = 20): List<BrainFact> = withContext(Dispatchers.IO) {
        reviewCandidates(dao().allLive(), System.currentTimeMillis(), limit)
    }

    /** Merge leftovers, expire temporary rows, enforce caps. Nothing user-confirmed is touched. */
    suspend fun consolidate(): ConsolidationReport = withContext(Dispatchers.IO) {
        var merged = 0
        val notes = mutableListOf<String>()
        val expired = dao().purgeExpiredTemporary(System.currentTimeMillis())
        if (expired > 0) notes.add("$expired expired temporary ${if (expired == 1) "memory" else "memories"} removed.")
        // Pairwise near-duplicate sweep (small N; facts table stays small by design).
        val live = dao().allLive().filter { it.supersededBy == null }
        val consumed = HashSet<String>()
        for (i in live.indices) {
            val a = live[i]
            if (a.id in consumed || a.verification == Verification.USER_CONFIRMED) continue
            val av = vecOf(a.embedding)
            if (av.isEmpty()) continue
            for (j in i + 1 until live.size) {
                val b = live[j]
                if (b.id in consumed || b.kind != a.kind) continue
                val bv = vecOf(b.embedding)
                if (bv.size != av.size) continue
                if (cosineSim(av, bv) >= DEDUP_COSINE) {
                    val keep = if (rankOf(a.verification) >= rankOf(b.verification)) a else b
                    val drop = if (keep == a) b else a
                    dao().upsert(drop.copy(supersededBy = keep.id, updatedAt = System.currentTimeMillis()))
                    dao().deleteFts(drop.id)
                    dao().upsert(keep.copy(updatedAt = System.currentTimeMillis()))
                    consumed.add(drop.id)
                    merged++
                }
            }
        }
        if (merged > 0) notes.add("$merged duplicate ${if (merged == 1) "memory" else "memories"} merged (originals kept as superseded).")
        val candidates = reviewCandidates(dao().allLive(), System.currentTimeMillis())
        if (candidates.isNotEmpty()) {
            notes.add("${candidates.size} confident inference${if (candidates.size == 1) "" else "s"} ready for your review below.")
        }
        ConsolidationReport(merged, expired, candidates.size, notes)
    }

    suspend fun confirm(id: String): Boolean = withContext(Dispatchers.IO) {
        val f = dao().byId(id) ?: return@withContext false
        if (f.verification == Verification.SUPERSEDED) return@withContext false
        dao().upsert(f.copy(verification = Verification.USER_CONFIRMED, confidence = 1.0, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun edit(id: String, newText: String): Boolean = withContext(Dispatchers.IO) {
        val f = dao().byId(id) ?: return@withContext false
        val clean = newText.trim().take(2000)
        if (clean.length < 3) return@withContext false
        val vec = embedText(clean) ?: emptyList()
        dao().upsert(
            f.copy(
                text = clean,
                verification = Verification.USER_CONFIRMED,
                confidence = 1.0,
                embedding = if (vec.isEmpty()) f.embedding else csvOf(vec),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        syncFts(f.id, clean, f.kind)
        true
    }

    suspend fun supersede(id: String, byId: String): Boolean = withContext(Dispatchers.IO) {
        val f = dao().byId(id) ?: return@withContext false
        dao().upsert(f.copy(verification = Verification.SUPERSEDED, supersededBy = byId, updatedAt = System.currentTimeMillis()))
        true
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao().delete(id)
        dao().deleteFts(id)
        db.links().deleteFor(id)
    }

    private fun strongerOf(a: String, b: String): String {
        val order = mapOf(
            Verification.USER_CONFIRMED to 4,
            Verification.IMPORTED to 3,
            Verification.INFERRED to 2,
            Verification.TEMPORARY to 1,
            Verification.SUPERSEDED to 0,
        )
        return if ((order[a] ?: 0) >= (order[b] ?: 0)) a else b
    }

    private fun rankOf(v: String): Int = when (v) {
        Verification.USER_CONFIRMED -> 4
        Verification.IMPORTED -> 3
        Verification.INFERRED -> 2
        Verification.TEMPORARY -> 1
        else -> 0
    }

    companion object {
        /**
         * Inferred facts worth a human look: confident, settled for a few
         * days, never confirmed. Pure function over live rows so the rule
         * is unit-testable.
         */
        fun reviewCandidates(facts: List<BrainFact>, now: Long, limit: Int = 20): List<BrainFact> {
            val threeDays = 3L * 24 * 60 * 60 * 1000
            return facts
                .filter { it.verification == Verification.INFERRED && it.supersededBy == null }
                .filter { it.confidence >= 0.8 && now - it.updatedAt >= threeDays }
                .sortedByDescending { it.confidence }
                .take(limit)
        }

        private val FTS_OPERATORS = setOf("and", "or", "not", "near")

        /**
         * Makes raw user text safe for an FTS4 MATCH query: keeps plain
         * content tokens only (letters/digits), drops operators, quotes,
         * wildcards and parens, caps length. Blank means "skip FTS".
         */
        fun sanitizeFtsQuery(raw: String): String {
            return raw.lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 && it !in FTS_OPERATORS }
                .take(8)
                .joinToString(" ")
        }

        fun flagCheck(newText: String, existing: List<BrainFact>): List<ContradictionFlag> {
            val out = mutableListOf<ContradictionFlag>()
            val newToks = contentTokens(newText)
            if (newToks.size < 3) return out
            val newNums = numbersOf(newText)
            val newNeg = hasNegation(newText)
            for (e in existing) {
                if (e.verification == Verification.SUPERSEDED) continue
                val oldToks = contentTokens(e.text)
                if (oldToks.size < 3) continue
                val overlap = newToks.intersect(oldToks).size
                if (overlap < 3) continue
                val oldNums = numbersOf(e.text)
                val numsDiffer = newNums.isNotEmpty() && oldNums.isNotEmpty() && newNums != oldNums
                val negFlip = newNeg != hasNegation(e.text) && (newNeg || hasNegation(e.text))
                if (numsDiffer) {
                    out.add(ContradictionFlag(e, "Heuristic: same subject but different numbers (${oldNums.joinToString()} vs ${newNums.joinToString()})."))
                } else if (negFlip) {
                    out.add(ContradictionFlag(e, "Heuristic: same subject but one statement negates the other."))
                }
            }
            return out.take(5)
        }

        private val STOP = setOf(
            "the", "and", "for", "with", "this", "that", "from", "have", "were", "been",
            "about", "into", "what", "when", "where", "your", "you", "was", "are", "will",
            "with", "has", "had", "but", "not", "all", "any", "can", "our", "out",
        )

        fun contentTokens(text: String): Set<String> =
            text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+"))
                .filter { it.length >= 4 && it !in STOP }.toSet()

        fun numbersOf(text: String): Set<String> {
            val found = Regex("\\d+(?:[\\.,]\\d+)?").findAll(text).map { it.value }.toMutableSet()
            // Common English number words so "thirty minutes" vs "45 minutes" still collides.
            val words = mapOf(
                "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
                "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
                "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
                "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
                "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
                "forty" to "40", "fifty" to "50", "sixty" to "60", "hundred" to "100",
            )
            val toks = text.lowercase().replace(Regex("[^a-z ]"), " ").split(Regex("\\s+"))
            for (i in toks.indices) {
                val w = words[toks[i]] ?: continue
                val next = toks.getOrNull(i + 1)?.let(words::get)
                found.add(if (next != null && w.toIntOrNull() != null && w.toInt() < 100 && w.toInt() % 10 == 0) (w.toInt() + next.toInt()).toString() else w)
            }
            return found
        }

        fun hasNegation(text: String): Boolean {
            val t = " ${text.lowercase()} "
            return listOf(" not ", " no ", " never ", " don't ", " doesn't ", " can't ", " won't ", " isn't ", " aren't ").any { it in t }
        }
    }
}
