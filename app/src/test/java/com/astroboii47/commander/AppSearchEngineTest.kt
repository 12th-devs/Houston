package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class AppSearchEngineTest {
    @Test fun searchApps_allShortcut() {
        val result = AppSearchShortcuts.fixedShortcut("all")
        assertNotNull(result)
        assertEquals("internal:all-apps", result!!.packageName)
    }

    @Test fun searchApps_recentsShortcut() {
        assertEquals("internal:recent-apps", AppSearchShortcuts.fixedShortcut("rec")!!.packageName)
        assertEquals("internal:recent-apps", AppSearchShortcuts.fixedShortcut("recents")!!.packageName)
        assertNull(AppSearchShortcuts.fixedShortcut("hello"))
    }

    @Test fun destinations_settings() {
        val dests = AppSearchShortcuts.destinations("settings")
        assertTrue(dests.any { it.packageName == "internal:command-settings" })
    }

    @Test fun destinations_emptyForGarbage() {
        assertTrue(AppSearchShortcuts.destinations("xyzqqq").isEmpty())
    }
}
