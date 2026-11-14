package com.astroboii47.commander

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {

    @Test
    fun convert_length() {
        assertEquals("1000 m", UnitConverter.convert("1 km in m"))
    }

    @Test
    fun convert_temperature() {
        assertEquals("32 °F", UnitConverter.convert("0 c to f"))
    }

    @Test
    fun convert_rejectsMismatchedGroups() {
        assertNull(UnitConverter.convert("1 km in grams"))
    }

    @Test
    fun convert_rejectsGarbage() {
        assertNull(UnitConverter.convert("hello world"))
        assertNull(UnitConverter.convert("1 km in"))
    }
}
