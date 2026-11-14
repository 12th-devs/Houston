package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class UnifiedAggregatorTest {
    private fun app(id: String, title: String) = RankCandidate(id, title, "", UnifiedSource.APP, emptyList())
    private fun setting(id: String, title: String) =
        RankCandidate(id, title, "subtitle", UnifiedSource.SETTING, listOf("bluetooth"))

    @Test fun triggersAreNotRankedHere() {
        // Explicit triggers must be routed to scoped providers, not the unified list.
        val intent = SearchIntentParser.parse("@mom")
        assertTrue(intent is SearchIntent.Trigger)
        assertEquals('@', (intent as SearchIntent.Trigger).symbol)
        assertEquals("mom", intent.rest)
    }

    @Test fun plainQueryCombinesSources() {
        val candidates = listOf(
            app("app:com.spotify.music", "Spotify"),
            setting("setting:bluetooth", "Bluetooth"),
            UnifiedAggregator.webCandidate("bluetooth"),
        )
        val ranked = UnifiedAggregator.rank("bluetooth", candidates, RankingSignals(), SearchIntent.Plain, limit = 10)
        // Strong text match (Bluetooth setting, exact) outranks the web fallback.
        assertEquals("setting:bluetooth", ranked.first().candidate.id)
        assertTrue(ranked.any { it.candidate.source == UnifiedSource.APP || it.candidate.source == UnifiedSource.WEB })
    }

    @Test fun deduplicationByStableId() {
        val dupes = listOf(
            app("app:x", "Spotify"),
            app("app:x", "Spotify"),
            app("app:y", "Spotify"),
        )
        val ranked = UnifiedAggregator.rank("spot", dupes, RankingSignals(), limit = 10)
        assertEquals(ranked.map { it.candidate.id }.toSet().size, ranked.size)
    }

    @Test fun intentBoostDoesNotHideResults() {
        val candidates = listOf(
            app("app:com.spotify.music", "Spotify"),
            setting("setting:bluetooth", "Bluetooth"),
        )
        val plain = UnifiedAggregator.rank("bluetooth", candidates, RankingSignals(), SearchIntent.Plain)
        val hinted = UnifiedAggregator.rank(
            "bluetooth",
            candidates,
            RankingSignals(),
            SearchIntent.SystemHint("bluetooth"),
        )
        // Same result set, only order may shift toward settings.
        assertEquals(plain.map { it.candidate.id }.toSet(), hinted.map { it.candidate.id }.toSet())
    }

    @Test fun asyncMergeKeepsStability() {
        val fast = listOf(app("app:a", "Spotify"), app("app:b", "Spotify Clone"))
        val signals = RankingSignals()
        val current = UnifiedAggregator.rank("spotify", fast, signals, limit = 10)
        val late = listOf(RankCandidate("contact:mom:", "Mom", "mobile", UnifiedSource.CONTACT, emptyList()))
        val merged = UnifiedAggregator.mergeAsync("spotify", current, late, signals, limit = 10)
        // Late contact does not match "spotify": visible app order is unchanged.
        assertEquals(current.map { it.candidate.id }, merged.map { it.candidate.id })
        val matchingLate = listOf(RankCandidate("app:spotify-lite", "Spotify Lite", "", UnifiedSource.APP, emptyList()))
        val merged2 = UnifiedAggregator.mergeAsync("spotify", current, matchingLate, signals, limit = 10)
        assertTrue(merged2.any { it.candidate.id == "app:spotify-lite" })
        // Deterministic re-rank equals a fresh full rank.
        val fresh = UnifiedAggregator.rank("spotify", fast + matchingLate, signals, limit = 10)
        assertEquals(fresh.map { it.candidate.id }, merged2.map { it.candidate.id })
    }

    @Test fun limitIsRespected() {
        val many = (0 until 50).map { app("app:$it", "App $it Test") }
        val ranked = UnifiedAggregator.rank("test", many, RankingSignals(), limit = 5)
        assertEquals(5, ranked.size)
    }
}
