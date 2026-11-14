package com.astroboii47.commander

import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * On-device text embedding, v1 ("HoustonHash-128").
 *
 * Dependency-free and fully offline: word unigrams + bigrams hashed into
 * 128 buckets with a signed random projection (hashing trick), then L2
 * normalized so cosine similarity is a dot product. Deterministic across
 * runs and devices — no model download, no network, no PII leaves the phone.
 *
 * This is a heuristic baseline, not a transformer. The typed boundary
 * ([embedText]/[cosineSim]) is replaceable: a future provider can swap in
 * a real on-device model without touching storage or search code. Scores
 * are always blended with literal full-text hits, so exact phrases still win.
 */
const val EMBEDDING_DIM = 128
const val EMBEDDING_MODEL = "houston-hash-128/v1"

private val EMB_STOP = setOf(
    "the", "and", "for", "with", "this", "that", "from", "have", "were", "been",
    "about", "into", "what", "when", "where", "your", "you", "was", "are", "had",
    "has", "but", "not", "all", "any", "can", "our", "out", "who", "how", "did",
    "just", "like", "over", "than", "then", "them", "there", "these", "will",
)

private fun tokens(text: String): List<String> {
    val words = text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+"))
        .map { it.trim() }.filter { it.length >= 3 && it !in EMB_STOP }
    if (words.isEmpty()) return emptyList()
    val grams = words.toMutableList()
    for (i in 0 until words.size - 1) grams.add(words[i] + "_" + words[i + 1])
    return grams
}

private fun hashToken(token: String): Pair<Int, Float> {
    val md = MessageDigest.getInstance("SHA-256")
    val h = md.digest(token.toByteArray(Charsets.UTF_8))
    var idx = 0
    for (i in 0 until 4) idx = (idx shl 8) or (h[i].toInt() and 0xFF)
    idx = Math.floorMod(idx, EMBEDDING_DIM)
    val sign = if ((h[4].toInt() and 1) == 0) 1f else -1f
    return idx to sign
}

/** Deterministic L2-normalized embedding. Empty text → null (no vector). */
fun embedText(text: String, dim: Int = EMBEDDING_DIM): List<Float>? {
    val toks = tokens(text)
    if (toks.isEmpty()) return null
    val vec = FloatArray(dim)
    for (t in toks) {
        val (idx, sign) = hashToken(t)
        if (idx in 0 until dim) vec[idx] += sign
    }
    var norm = 0.0
    for (v in vec) norm += v * v
    norm = sqrt(norm)
    if (norm < 1e-9) return null
    return vec.map { (it / norm).toFloat() }
}

fun cosineSim(a: List<Float>, b: List<Float>): Double {
    if (a.isEmpty() || a.size != b.size) return 0.0
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    if (na < 1e-9 || nb < 1e-9) return 0.0
    return (dot / sqrt(na * nb)).coerceIn(-1.0, 1.0)
}

/** Memory text eligible for embedding: user-authored first, generated last. */
fun memoryEmbedSource(m: MemoryRecord): String =
    listOf(m.title, m.body, m.note, m.tags.joinToString(" "), m.summary)
        .filter { it.isNotBlank() }.joinToString("\n").take(2000)

/**
 * Blended semantic + literal ranking over candidates. Pure function so the
 * scoring contract is unit-testable; storage lives in [MemoryStore].
 */
fun rankSemantic(
    query: String,
    candidates: List<MemoryRecord>,
    limit: Int = 50,
): List<Pair<MemoryRecord, Double>> {
    val q = embedText(query) ?: return emptyList()
    val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
    return candidates.mapNotNull { m ->
        val emb = m.embedding?.takeIf { it.size == EMBEDDING_DIM } ?: embedText(memoryEmbedSource(m))
        if (emb == null) return@mapNotNull null
        val cos = (cosineSim(q, emb) + 1.0) / 2.0 // 0..1
        val hay = "${m.title}\n${m.body}\n${m.note}\n${m.tags.joinToString(" ")}".lowercase()
        val literal = if (terms.isEmpty()) 0.0 else terms.count { it in hay }.toDouble() / terms.size
        val score = 0.65 * cos + 0.35 * literal
        if (score < 0.42) null else m to score
    }.sortedByDescending { it.second }.take(limit)
}
