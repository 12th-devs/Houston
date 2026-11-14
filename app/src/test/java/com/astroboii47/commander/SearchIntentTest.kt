package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class SearchIntentTest {
    @Test fun triggersArePreserved() {
        val symbols = listOf('@', '#', '-', '!', '+', '*', ',', '/', '?', ' ')
        for (symbol in symbols) {
            val parsed = SearchIntentParser.parse("${symbol}hello", ' ', '!')
            assertTrue("'$symbol' must stay explicit, got $parsed", parsed is SearchIntent.Trigger)
            assertEquals(symbol, (parsed as SearchIntent.Trigger).symbol)
        }
        // Custom file trigger + swapped note symbol are respected.
        assertTrue(SearchIntentParser.parse(".note", '.', '.') is SearchIntent.Trigger)
        assertTrue(SearchIntentParser.parse("!note", ' ', '!') is SearchIntent.Trigger)
    }

    @Test fun plainQueriesAreNotTriggers() {
        assertTrue(SearchIntentParser.parse("spotify") is SearchIntent.Plain ||
            SearchIntentParser.parse("spotify") is SearchIntent.PersonHint)
        assertFalse(SearchIntentParser.isExplicitTrigger("spotify"))
        assertFalse(SearchIntentParser.isExplicitTrigger("15 * 24"))
    }

    @Test fun calculatorDetection() {
        val calc = SearchIntentParser.parse("15 * 24")
        assertTrue(calc is SearchIntent.Calculator)
        assertEquals("15 * 24", (calc as SearchIntent.Calculator).expression)
        assertTrue(SearchIntentParser.parse("(2 + 3) * 4") is SearchIntent.Calculator)
        // Unit conversion is separate from arithmetic.
        assertTrue(SearchIntentParser.parse("5 km in miles") is SearchIntent.UnitConversion)
    }

    @Test fun timerDetection() {
        val timer = SearchIntentParser.parse("5 minute timer")
        assertTrue(timer is SearchIntent.Timer)
        val t = timer as SearchIntent.Timer
        assertEquals(300L, t.seconds)
        assertTrue(SearchIntentParser.parse("timer 10m tea") is SearchIntent.Timer)
        // A bare duration without timer words is plain (avoids hijacking "5 minutes" notes).
        assertFalse(SearchIntentParser.parse("5 minutes") is SearchIntent.Timer)
    }

    @Test fun webServiceShortcuts() {
        val yt = SearchIntentParser.parse("youtube cats")
        assertTrue(yt is SearchIntent.WebService)
        assertEquals("youtube", (yt as SearchIntent.WebService).targetId)
        assertEquals("cats", yt.query)
        // Bare service names stay plain so the app itself can rank first.
        assertFalse(SearchIntentParser.parse("spotify") is SearchIntent.WebService)
        assertFalse(SearchIntentParser.parse("youtube") is SearchIntent.WebService)
    }

    @Test fun systemAndDownloadsHints() {
        assertTrue(SearchIntentParser.parse("bluetooth") is SearchIntent.SystemHint)
        assertTrue(SearchIntentParser.parse("brightness 50") is SearchIntent.SystemHint)
        val downloads = SearchIntentParser.parse("downloads")
        assertTrue(downloads is SearchIntent.Downloads)
        assertTrue(SearchIntentParser.parse("downloads invoice") is SearchIntent.Downloads)
    }

    @Test fun personHint() {
        assertTrue(SearchIntentParser.parse("mom") is SearchIntent.PersonHint)
        // Multi-word noise and commands are not person hints.
        assertFalse(SearchIntentParser.parse("5 minute timer") is SearchIntent.PersonHint)
    }
}
