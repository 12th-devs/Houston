package com.astroboii47.commander

/**
 * Unified relevance model shared by the command overlay and the native search surface.
 *
 * Pure Kotlin (no Android dependencies) so ranking is unit-testable on the JVM.
 *
 * Scoring is intentionally tiered and explainable:
 * - Text tiers from [SearchMatch] dominate: EXACT(200) > PREFIX(150) >
 *   WORD_PREFIX(100) > SUBSTRING(50-59) > FUZZY(25-29) > NONE(0).
 * - Learned history adds at most [MAX_HISTORY_BONUS] (20).
 * - App usage adds at most [MAX_USAGE_BONUS] (12).
 * - Source relevance adds at most [MAX_SOURCE_BONUS] (8).
 *
 * The largest combined bonus (40) is smaller than the gap between text tiers
 * (50), so strong text relevance always wins while frequency/recency decides
 * ties inside the same tier. Sorting is fully deterministic:
 * total desc, then title asc, then id asc.
 */
enum class UnifiedSource(val bonus: Int, val label: String) {
    COMMAND(8, "command"),
    APP(6, "app"),
    CONTACT(6, "contact"),
    CONVERSATION(6, "message"),
    SETTING(6, "setting"),
    CONTROL(6, "control"),
    SHORTCUT(5, "shortcut"),
    FOLDER(5, "folder"),
    FILE(4, "file"),
    TASK(4, "task"),
    ALIAS(4, "search"),
    HISTORY(2, "history"),
    WEB(1, "web"),
}

data class RankCandidate(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val source: UnifiedSource = UnifiedSource.APP,
    /** Extra searchable strings: package name, keywords, custom terms, number. */
    val aliases: List<String> = emptyList(),
)

/** Learned + usage signals supplied by the Android layer. All maps keyed by [RankCandidate.id]. */
data class RankingSignals(
    val counts: Map<String, Int> = emptyMap(),
    val lastSelectedAtMillis: Map<String, Long> = emptyMap(),
    val nowMillis: Long = System.currentTimeMillis(),
    /** Normalized foreground/launch weight per id, 0.0..1.0. Absent entries are 0. */
    val usage: Map<String, Double> = emptyMap(),
)

data class ScoredCandidate(
    val candidate: RankCandidate,
    val textKind: SearchMatch.MatchKind,
    val textScore: Int,
    val historyBonus: Int,
    val usageBonus: Int,
    val sourceBonus: Int,
    val total: Int,
    val explanation: String,
)

object UnifiedRanker {
    const val MAX_HISTORY_BONUS = 20
    const val MAX_USAGE_BONUS = 12
    const val MAX_SOURCE_BONUS = 8

    fun historyBonus(id: String, signals: RankingSignals): Int {
        val count = signals.counts[id]?.coerceIn(0, 1000) ?: 0
        if (count <= 0) return 0
        // Logarithmic frequency so a 200-count item does not permanently dominate.
        val frequency = when {
            count >= 50 -> 12
            count >= 20 -> 10
            count >= 8 -> 8
            count >= 3 -> 6
            else -> 4
        }
        val last = signals.lastSelectedAtMillis[id]
        val recency = if (last == null || last <= 0L) 0 else {
            val age = signals.nowMillis - last
            when {
                age < 0 -> 0
                age <= 60L * 60 * 1000 -> 8
                age <= 24L * 60 * 60 * 1000 -> 5
                age <= 7L * 24 * 60 * 60 * 1000 -> 3
                else -> 1
            }
        }
        return (frequency + recency).coerceAtMost(MAX_HISTORY_BONUS)
    }

    fun usageBonus(id: String, signals: RankingSignals): Int {
        val weight = signals.usage[id]?.coerceIn(0.0, 1.0) ?: 0.0
        if (weight <= 0.0) return 0
        return (weight * MAX_USAGE_BONUS).toInt().coerceIn(1, MAX_USAGE_BONUS)
    }

    fun scoreOne(query: String, candidate: RankCandidate, signals: RankingSignals = RankingSignals()): ScoredCandidate {
        val match = SearchMatch.score(query, candidate.title, candidate.aliases)
        val history = historyBonus(candidate.id, signals)
        val usage = usageBonus(candidate.id, signals)
        val source = candidate.source.bonus.coerceIn(0, MAX_SOURCE_BONUS)
        val total = match.score + history + usage + source
        val explanation = buildString {
            append(match.kind.name.lowercase())
            append(" ${match.score}")
            if (history > 0) append(" +history $history")
            if (usage > 0) append(" +usage $usage")
            append(" +source $source = $total")
        }
        return ScoredCandidate(candidate, match.kind, match.score, history, usage, source, total, explanation)
    }

    /**
     * Rank [candidates] for [query]. Candidates with no text match are dropped
     * unless they carry learned history (keeps a frequently used result findable
     * under a weak alias). Ordering is stable and deterministic.
     */
    fun rank(
        query: String,
        candidates: List<RankCandidate>,
        signals: RankingSignals = RankingSignals(),
        limit: Int = Int.MAX_VALUE,
    ): List<ScoredCandidate> {
        val needle = query.trim()
        if (needle.isBlank()) {
            // Empty query: order by learned relevance, then usage, then title.
            return candidates
                .map { scoreOne("", it, signals) }
                .sortedWith(
                    compareByDescending<ScoredCandidate> { it.historyBonus }
                        .thenByDescending { it.usageBonus }
                        .thenByDescending { it.sourceBonus }
                        .thenBy { it.candidate.title.lowercase() }
                        .thenBy { it.candidate.id },
                )
                .take(limit.coerceAtLeast(0))
        }
        return candidates
            .map { scoreOne(needle, it, signals) }
            .filter { it.textKind != SearchMatch.MatchKind.NONE || it.historyBonus >= 6 }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.total }
                    .thenByDescending { it.textScore }
                    .thenByDescending { it.historyBonus }
                    .thenByDescending { it.usageBonus }
                    .thenBy { it.candidate.title.lowercase() }
                    .thenBy { it.candidate.id },
            )
            .take(limit.coerceAtLeast(0))
    }

    /** Stable re-sort guard: equal totals keep their previous relative order via id/title tie-breaks. */
    fun isStableOrder(first: List<ScoredCandidate>, second: List<ScoredCandidate>): Boolean {
        if (first.map { it.candidate.id } != second.map { it.candidate.id }) return false
        return true
    }
}
