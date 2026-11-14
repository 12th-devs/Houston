package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class SearchMatchTest {
    @Test fun exactBeatsPrefix() {
        val exact = SearchMatch.score("spotify", "Spotify")
        val prefix = SearchMatch.score("spot", "Spotify Premium")
        assertEquals(SearchMatch.MatchKind.EXACT, exact.kind)
        assertEquals(SearchMatch.MatchKind.PREFIX, prefix.kind)
        assertTrue(exact.score > prefix.score)
    }

    @Test fun prefixBeatsWordPrefixBeatsSubstring() {
        val prefix = SearchMatch.score("blue", "Bluetooth")
        val wordPrefix = SearchMatch.score("blue", "Dark Blue Mode")
        val substring = SearchMatch.score("lue", "Bluetooth")
        assertEquals(SearchMatch.MatchKind.PREFIX, prefix.kind)
        assertEquals(SearchMatch.MatchKind.WORD_PREFIX, wordPrefix.kind)
        assertEquals(SearchMatch.MatchKind.SUBSTRING, substring.kind)
        assertTrue(prefix.score > wordPrefix.score)
        assertTrue(wordPrefix.score > substring.score)
    }

    @Test fun typoTolerance() {
        // Transposed + substituted typos still match.
        assertTrue(SearchMatch.isMatch("spofity", "Spotify"))
        assertTrue(SearchMatch.isMatch("bluetoth", "Bluetooth"))
        assertTrue(SearchMatch.isMatch("dowloads", "Downloads"))
        val fuzzy = SearchMatch.score("spofity", "Spotify")
        assertEquals(SearchMatch.MatchKind.FUZZY, fuzzy.kind)
        // Unrelated strings do not match.
        assertEquals(SearchMatch.MatchKind.NONE, SearchMatch.score("xyzqqq", "Spotify").kind)
    }

    @Test fun aliasesCount() {
        val viaAlias = SearchMatch.score("ps", "Play Store", listOf("ps", "playstore"))
        assertTrue(viaAlias.score > 0)
    }

    @Test fun blankQueryNeverMatches() {
        assertEquals(SearchMatch.MatchKind.NONE, SearchMatch.score("", "Spotify").kind)
        assertEquals(SearchMatch.MatchKind.NONE, SearchMatch.score("   ", "Spotify").kind)
    }

    @Test fun deterministic() {
        val a = SearchMatch.score("mom", "Mom")
        val b = SearchMatch.score("mom", "Mom")
        assertEquals(a, b)
    }
}
