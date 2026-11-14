package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class ContactEngineTest {
    @Test fun canonicalPersonName_normalizes() {
        assertEquals("john doe", ContactNameLogic.canonicalize("  John   DOE "))
        assertEquals("o brien", ContactNameLogic.canonicalize("O'Brien"))
        assertEquals("", ContactNameLogic.canonicalize("   "))
    }

    @Test fun contactNameMatchRank_orders() {
        assertEquals(0, ContactNameLogic.rank("John Doe", "john doe"))
        assertEquals(1, ContactNameLogic.rank("John Doe", "john"))
        assertEquals(2, ContactNameLogic.rank("John Doe", "doe"))
        assertEquals(3, ContactNameLogic.rank("John Doe", "xyz"))
    }
}
