package com.astroboii47.commander

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoistNaturalLanguageTest {

    @Test
    fun expandCompactTimes_convertsHmm() {
        assertEquals("meet at 1:45", TodoistNaturalLanguage.expandCompactTimes("meet at 145"))
    }

    @Test
    fun expandCompactTimes_convertsHHmm() {
        assertEquals("lunch 07:45", TodoistNaturalLanguage.expandCompactTimes("lunch 0745"))
        assertEquals("dinner 21:40", TodoistNaturalLanguage.expandCompactTimes("dinner 2140"))
    }

    @Test
    fun expandCompactTimes_leavesNormalText() {
        assertEquals("call mom", TodoistNaturalLanguage.expandCompactTimes("call mom"))
        // Years / long numbers must not be treated as times.
        assertEquals("in 2026", TodoistNaturalLanguage.expandCompactTimes("in 2026"))
    }

    @Test
    fun detectedDuePreview_dateOnly() {
        val preview = TodoistNaturalLanguage.detectedDuePreview("buy milk tomorrow")
        assertEquals("Tomorrow", preview)
    }

    @Test
    fun detectedDuePreview_timeWithMeridiem() {
        val preview = TodoistNaturalLanguage.detectedDuePreview("standup at 9am")
        assertTrue(preview.toString().contains("9 AM"))
    }

    @Test
    fun detectedDuePreview_compactTime() {
        val preview = TodoistNaturalLanguage.detectedDuePreview("dentist 1450")
        // 14:50 -> inferred 2:50 PM via compact-time path.
        assertTrue(preview.toString().contains("2:50 PM"))
    }

    @Test
    fun detectedDuePreview_hashtag() {
        val preview = TodoistNaturalLanguage.detectedDuePreview("write report #work")
        assertTrue(preview.toString().contains("#work"))
    }

    @Test
    fun detectedDuePreview_blankWhenNothingDetected() {
        assertNull(TodoistNaturalLanguage.detectedDuePreview("buy milk"))
    }
}
