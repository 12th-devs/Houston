package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class UnifiedRankerTest {
    private fun candidate(id: String, title: String, source: UnifiedSource = UnifiedSource.APP) =
        RankCandidate(id, title, "", source, emptyList())

    @Test fun strongTextAlwaysWinsOverHistory() {
        val exact = candidate("app:obscure", "Bluetooth")
        val frequentPrefix = candidate("app:frequent", "Bluetooth Speaker")
        val signals = RankingSignals(
            counts = mapOf("app:frequent" to 500),
            lastSelectedAtMillis = mapOf("app:frequent" to System.currentTimeMillis()),
            nowMillis = System.currentTimeMillis(),
            usage = mapOf("app:frequent" to 1.0),
        )
        val ranked = UnifiedRanker.rank("bluetooth", listOf(frequentPrefix, exact), signals)
        // EXACT(200) beats PREFIX(150) + max bonus (40).
        assertEquals("app:obscure", ranked.first().candidate.id)
    }

    @Test fun frequencyBreaksTiesInsideTier() {
        val a = candidate("app:a", "Spotify")
        val b = candidate("app:b", "Spotify Clone")
        // Both are prefix matches for "spot"? Make them same tier via identical titles.
        val c = candidate("app:c", "Spotify")
        val signals = RankingSignals(
            counts = mapOf("app:c" to 10),
            lastSelectedAtMillis = mapOf("app:c" to System.currentTimeMillis()),
            nowMillis = System.currentTimeMillis(),
        )
        val ranked = UnifiedRanker.rank("spotify", listOf(a, c), signals)
        assertEquals("app:c", ranked.first().candidate.id)
        assertTrue(ranked.first().historyBonus > 0)
    }

    @Test fun usageBoostsButDoesNotBeatTier() {
        val exact = candidate("id:exact", "Timer")
        val prefix = candidate("id:prefix", "Timer Plus")
        val signals = RankingSignals(
            usage = mapOf("id:prefix" to 1.0),
            nowMillis = System.currentTimeMillis(),
        )
        val ranked = UnifiedRanker.rank("timer", listOf(prefix, exact), signals)
        assertEquals("id:exact", ranked.first().candidate.id)
    }

    @Test fun deterministicTieBreak() {
        val b = candidate("id:b", "Same")
        val a = candidate("id:a", "Same")
        val first = UnifiedRanker.rank("same", listOf(b, a))
        val second = UnifiedRanker.rank("same", listOf(b, a))
        assertEquals(first.map { it.candidate.id }, second.map { it.candidate.id })
        assertEquals("id:a", first.first().candidate.id)
    }

    @Test fun blankQueryOrdersByHistoryThenTitle() {
        val a = candidate("id:a", "Zebra")
        val b = candidate("id:b", "Apple")
        val signals = RankingSignals(counts = mapOf("id:a" to 5), nowMillis = System.currentTimeMillis())
        val ranked = UnifiedRanker.rank("", listOf(a, b), signals)
        assertEquals("id:a", ranked.first().candidate.id)
        val noHistory = UnifiedRanker.rank("", listOf(a, b))
        assertEquals("id:b", noHistory.first().candidate.id)
    }

    @Test fun recencyDecays() {
        val now = 1_700_000_000_000L
        val recent = UnifiedRanker.historyBonus(
            "x",
            RankingSignals(mapOf("x" to 3), mapOf("x" to now - 30 * 60 * 1000), now),
        )
        val old = UnifiedRanker.historyBonus(
            "x",
            RankingSignals(mapOf("x" to 3), mapOf("x" to now - 30L * 24 * 60 * 60 * 1000), now),
        )
        assertTrue(recent > old)
    }

    @Test fun explanationIsStable() {
        val scored = UnifiedRanker.scoreOne("spot", candidate("app:x", "Spotify"))
        assertTrue(scored.explanation.contains("prefix"))
        assertEquals(scored, UnifiedRanker.scoreOne("spot", candidate("app:x", "Spotify")))
    }
}
