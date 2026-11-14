package com.astroboii47.commander

/**
 * Fast, local, dependency-free text matching for unified search.
 *
 * Deterministic and explainable: every query/target pair maps to one
 * [MatchKind] and a tiered score. Tiers are spaced so a stronger text
 * tier always beats history/usage bonuses applied later in [UnifiedRanker].
 */
object SearchMatch {
    enum class MatchKind(val baseScore: Int) {
        EXACT(200),
        PREFIX(150),
        WORD_PREFIX(100),
        SUBSTRING(50),
        FUZZY(25),
        NONE(0),
    }

    data class Result(val kind: MatchKind, val score: Int)

    fun score(rawQuery: String, rawTarget: String, aliases: List<String> = emptyList()): Result {
        val query = normalize(rawQuery)
        if (query.isBlank()) return Result(MatchKind.NONE, 0)
        var best = matchOne(query, normalize(rawTarget))
        for (alias in aliases) {
            val candidate = matchOne(query, normalize(alias))
            if (candidate.score > best.score) best = candidate
        }
        return best
    }

    fun isMatch(rawQuery: String, rawTarget: String, aliases: List<String> = emptyList()): Boolean =
        score(rawQuery, rawTarget, aliases).kind != MatchKind.NONE

    fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun matchOne(query: String, target: String): Result {
        if (target.isBlank()) return Result(MatchKind.NONE, 0)
        if (target == query) return Result(MatchKind.EXACT, MatchKind.EXACT.baseScore)
        if (target.startsWith(query)) return Result(MatchKind.PREFIX, MatchKind.PREFIX.baseScore)
        val words = target.split(' ')
        if (words.any { it.startsWith(query) }) return Result(MatchKind.WORD_PREFIX, MatchKind.WORD_PREFIX.baseScore)
        val index = target.indexOf(query)
        if (index >= 0) {
            // Earlier substring positions rank slightly higher, but stay inside the tier.
            val bonus = ((target.length - index).coerceAtMost(9))
            return Result(MatchKind.SUBSTRING, MatchKind.SUBSTRING.baseScore + bonus.coerceIn(0, 9))
        }
        // Typo tolerance: small edit distance on the whole string or on any single word.
        val threshold = if (query.length <= 4) 1 else if (query.length <= 8) 2 else 3
        val whole = levenshtein(query, target, threshold)
        if (whole in 1..threshold) {
            return Result(MatchKind.FUZZY, MatchKind.FUZZY.baseScore + (threshold - whole) * 2)
        }
        for (word in words) {
            if (word.length < 3) continue
            val wordThreshold = if (word.length <= 4 || query.length <= 4) 1 else 2
            if (levenshtein(query, word, wordThreshold) in 1..wordThreshold) {
                return Result(MatchKind.FUZZY, MatchKind.FUZZY.baseScore)
            }
        }
        // Transposed adjacent letters ("spofity" -> "spotify") without full distance cost.
        if (isSingleTransposition(query, target)) return Result(MatchKind.FUZZY, MatchKind.FUZZY.baseScore)
        return Result(MatchKind.NONE, 0)
    }

    private fun isSingleTransposition(a: String, b: String): Boolean {
        if (a.length != b.length || a.length < 4) return false
        var first = -1
        for (i in a.indices) {
            if (a[i] != b[i]) {
                if (first == -1) first = i
                else if (i == first + 1 && a[first] == b[i] && a[i] == b[first]) {
                    // Only difference is this adjacent swap.
                    for (j in i + 1 until a.length) if (a[j] != b[j]) return false
                    return true
                } else return false
            }
        }
        return false
    }

    /** Levenshtein distance with early exit once [max] is exceeded. Returns max+1 when over budget. */
    fun levenshtein(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
