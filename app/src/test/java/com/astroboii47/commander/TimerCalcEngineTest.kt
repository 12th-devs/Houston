package com.astroboii47.commander

import org.junit.Assert.*
import org.junit.Test

class TimerCalcEngineTest {
    @Test fun alarmTimeLabel_parses() {
        assertEquals("6:30 AM", TimerCalcLogic.alarmTimeLabel("6:30am"))
        assertEquals("6:30 PM", TimerCalcLogic.alarmTimeLabel("6:30pm"))
        assertEquals("12:00 AM", TimerCalcLogic.alarmTimeLabel("12am"))
        assertNull(TimerCalcLogic.alarmTimeLabel("hello"))
        assertNull(TimerCalcLogic.alarmTimeLabel("25am"))
    }

    @Test fun timerInputPreview_timer() {
        assertEquals("timer · 10:00", TimerCalcLogic.timerInputPreview("10 minutes"))
        assertEquals("timer · 01:00:00", TimerCalcLogic.timerInputPreview("1 hour"))
        assertNull(TimerCalcLogic.timerInputPreview("hello"))
    }

    @Test fun timerInputPreview_prefersAlarm() {
        assertEquals("alarm · 6:30 AM", TimerCalcLogic.timerInputPreview("6:30am"))
    }

    @Test fun calculatorPreview_arithmetic() {
        assertEquals("360", TimerCalcLogic.calculatorPreview("15 * 24"))
        assertNull(TimerCalcLogic.calculatorPreview("hello world"))
    }

    @Test fun calculatorPreview_unitConverter() {
        assertEquals("1000 m", TimerCalcLogic.calculatorPreview("1 km in m"))
    }

    @Test fun calculate_formats() {
        assertEquals("2+2 = 4", TimerCalcLogic.calculate("2+2"))
        assertTrue(TimerCalcLogic.calculate("2+2")!!.startsWith("2+2 = "))
        assertEquals("Invalid calculation", TimerCalcLogic.calculate("hello"))
    }

    @Test fun normalizeCalculation_words() {
        assertEquals("4", TimerCalcLogic.calculatorPreview("2 plus 2"))
        assertEquals("6", TimerCalcLogic.calculatorPreview("2 times 3"))
    }
}
