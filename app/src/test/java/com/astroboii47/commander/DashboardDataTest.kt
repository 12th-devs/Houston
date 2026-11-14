package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class DashboardDataTest {
    @Test fun swimTimeFormatsAndParses() {
        assertEquals("1:08.20", formatSwimTime(68.2))
        assertEquals("1:05.00", formatSwimTime(65.0))
        assertEquals("0:59.50", formatSwimTime(59.5))
        assertEquals(68.2, parseSwimTime("1:08.20")!!, 1e-9)
        assertEquals(68.2, parseSwimTime("68.2")!!, 1e-9)
        assertNull(parseSwimTime("abc"))
        assertNull(parseSwimTime(""))
    }

    @Test fun swimProgressNeedsStartBestGoal() {
        assertNull(swimProgressPct(null, 68.2, 65.0))
        assertNull(swimProgressPct(75.0, 68.2, null))
        // (75 - 68.2) / (75 - 65) = 0.68
        assertEquals(0.68, swimProgressPct(75.0, 68.2, 65.0)!!, 1e-9)
        assertEquals(1.0, swimProgressPct(75.0, 65.0, 65.0)!!, 1e-9)
        assertEquals(0.0, swimProgressPct(75.0, 75.0, 65.0)!!, 1e-9)
        // Goal met or beaten clamps at 1.
        assertEquals(1.0, swimProgressPct(75.0, 64.0, 65.0)!!, 1e-9)
    }

    @Test fun weatherParsesOpenMeteo() {
        val body = """
            {"latitude":38.25,"longitude":-85.75,"daily":{
            "time":["2026-11-14","2026-11-15"],
            "temperature_2m_max":[49.0,51.0],
            "temperature_2m_min":[29.0,30.0],
            "weathercode":[3,61]}}
        """.trimIndent()
        val wx = parseOpenMeteo(body)!!
        assertEquals("2026-11-14", wx.date)
        assertEquals(49.0, wx.hiF, 1e-9)
        assertEquals(29.0, wx.loF, 1e-9)
        assertEquals(3, wx.code)
    }

    @Test fun weatherKindMapping() {
        assertEquals("sunny", wxKind(0))
        assertEquals("partly", wxKind(2))
        assertEquals("rain", wxKind(61))
        assertEquals("snow", wxKind(71))
        assertEquals("storm", wxKind(95))
        assertEquals("cloudy", wxKind(999))
    }

    @Test fun weatherRejectsBadPayloads() {
        assertNull(parseOpenMeteo("{}"))
        assertNull(parseOpenMeteo("not json"))
        assertNull(parseOpenMeteo("""{"daily":{"time":[]}}"""))
    }
}
