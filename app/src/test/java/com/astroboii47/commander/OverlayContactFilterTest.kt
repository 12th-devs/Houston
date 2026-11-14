package com.astroboii47.commander

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayContactFilterTest {

    @Test
    fun canonicalize_trimsSeparatorsAndLowercases() {
        assertEquals("al", OverlayContactFilter.canonicalize("Al"))
        assertEquals("hello world", OverlayContactFilter.canonicalize("  Hello-World "))
        assertEquals("zed al", OverlayContactFilter.canonicalize("Zed.Al"))
        assertEquals("", OverlayContactFilter.canonicalize("  "))
    }

    @Test
    fun matches_blankQueryMatchesAll() {
        assertTrue(OverlayContactFilter.matches("Albert", "555-1234", ""))
        assertTrue(OverlayContactFilter.matches("Albert", "555-1234", "   "))
    }

    @Test
    fun matches_nameOrLabel() {
        assertTrue(OverlayContactFilter.matches("Albert", "555-1234", "alb"))
        assertTrue(OverlayContactFilter.matches("Albert", "555-1234", "555"))
        assertFalse(OverlayContactFilter.matches("Albert", "555-1234", "zzz"))
    }

    @Test
    fun rank_tiers() {
        assertEquals(0, OverlayContactFilter.rank("Al", "al"))
        assertEquals(1, OverlayContactFilter.rank("Albert", "al"))
        assertEquals(2, OverlayContactFilter.rank("Zed Al", "al"))
        assertEquals(2, OverlayContactFilter.rank("Sal", "al"))
        assertEquals(3, OverlayContactFilter.rank("Bob", "al"))
    }

    @Test
    fun filterAndSort_drivesSearchOrder() {
        val names = listOf("Sal", "Zed Al", "Albert", "Al")
        val ordered = OverlayContactFilter.filterAndSort(names, "al")
        assertEquals(listOf("Al", "Albert", "Sal", "Zed Al"), ordered)
    }

    @Test
    fun filterAndSort_blankQuerySortsAlphabetically() {
        val names = listOf("Zed", "al", "Bob")
        assertEquals(listOf("al", "Bob", "Zed"), OverlayContactFilter.filterAndSort(names, ""))
    }
}
