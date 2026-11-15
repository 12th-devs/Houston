package com.astroboii47.commander

import com.astroboii47.commander.secondbrain.ui.SbDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SbDestinationTest {

    @Test
    fun `every legacy tab maps to a surviving destination`() {
        for (tab in DashTab.entries) {
            val dest = SbDestination.fromDashTab(tab)
            assertEquals(tab, SbDestination.toDashTab(dest))
        }
    }

    @Test
    fun `bottom bar holds exactly the four primary destinations`() {
        assertEquals(
            listOf(SbDestination.HOME, SbDestination.PLAN, SbDestination.MEMORY, SbDestination.ASK),
            SbDestination.bar,
        )
        assertTrue(SbDestination.bar.all { it.primary })
        assertEquals(
            listOf(SbDestination.SCHOOL, SbDestination.SWIM, SbDestination.CALENDAR, SbDestination.CAPTURES),
            SbDestination.entries.filter { !it.primary },
        )
    }

    @Test
    fun `deep-link targets resolve to destinations with payloads`() {
        assertEquals(SbDestination.SCHOOL to "0041", SbDestination.parseTarget("school:0041"))
        assertEquals(SbDestination.SWIM to null, SbDestination.parseTarget("swim:"))
        assertEquals(SbDestination.CAPTURES to "abc-uuid", SbDestination.parseTarget("memory:abc-uuid"))
        assertEquals(SbDestination.CAPTURES to "abc-uuid", SbDestination.parseTarget("memories:abc-uuid"))
        assertEquals(SbDestination.CALENDAR to "2026-11-14", SbDestination.parseTarget("calendar:2026-11-14"))
        assertEquals(SbDestination.HOME to null, SbDestination.parseTarget("home"))
        assertEquals(SbDestination.PLAN to null, SbDestination.parseTarget("plan"))
        assertEquals(SbDestination.ASK to null, SbDestination.parseTarget("ask"))
        assertEquals(SbDestination.MEMORY to null, SbDestination.parseTarget("agent-memory"))
    }

    @Test
    fun `unknown and blank targets fall back to home like the legacy router`() {
        assertEquals(SbDestination.HOME to null, SbDestination.parseTarget("notes:xyz"))
        assertEquals(SbDestination.HOME to null, SbDestination.parseTarget(""))
        assertEquals(SbDestination.HOME to null, SbDestination.parseTarget(null))
        val (destination, payload) = SbDestination.parseTarget("school:")
        assertEquals(SbDestination.SCHOOL, destination)
        assertNull(payload)
    }
}
