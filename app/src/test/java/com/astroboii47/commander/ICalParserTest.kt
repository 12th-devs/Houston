package com.astroboii47.commander

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ICalParserTest {
    @Test fun unfoldsFoldedLines() {
        val text = "BEGIN:VEVENT\r\nSUMMARY:Long tit\r\n le here\r\nDTSTART:20261114T090000Z\r\nEND:VEVENT\r\n"
        val events = parseICalendar(text)
        assertEquals(1, events.size)
        assertEquals("Long title here", events[0].summary)
    }

    @Test fun parsesSingleTimedEvent() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:abc-1
            SUMMARY:Parent night
            LOCATION:Main gym
            DTSTART:20261114T180000Z
            DTEND:20261114T193000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val events = parseICalendar(text)
        assertEquals(1, events.size)
        val e = events[0]
        assertFalse(e.start.allDay)
        assertEquals("Parent night", e.summary)
        assertEquals("Main gym", e.location)
        val occ = expandICalEvents(events, "f", "School", LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14))
        assertEquals(1, occ.size)
        assertEquals(LocalDate.of(2026, 11, 14), occ[0].date)
        assertNotNull(occ[0].startTime)
    }

    @Test fun parsesAllDayAndSkipsCancelled() {
        val text = """
            BEGIN:VEVENT
            UID:a1
            SUMMARY:No school
            DTSTART;VALUE=DATE:20261125
            DTEND;VALUE=DATE:20261127
            END:VEVENT
            BEGIN:VEVENT
            UID:a2
            SUMMARY:Gone
            STATUS:CANCELLED
            DTSTART;VALUE=DATE:20261126
            END:VEVENT
        """.trimIndent()
        val events = parseICalendar(text)
        assertEquals(1, events.size)
        assertTrue(events[0].start.allDay)
        val occ = expandICalEvents(events, "f", "School", LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26))
        // DTEND is exclusive: 25th and 26th.
        assertEquals(listOf(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26)), occ.map { it.date })
        assertTrue(occ.all { it.allDay })
    }

    @Test fun expandsDailyCount() {
        val text = """
            BEGIN:VEVENT
            UID:d1
            SUMMARY:Daily standup
            DTSTART:20261110T090000Z
            RRULE:FREQ=DAILY;COUNT=3
            END:VEVENT
        """.trimIndent()
        val events = parseICalendar(text)
        assertEquals(1, events.size)
        val occ = expandICalEvents(events, "f", "W", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12)),
            occ.map { it.date },
        )
    }

    @Test fun expandsWeeklyByDayWithUntil() {
        val text = """
            BEGIN:VEVENT
            UID:w1
            SUMMARY:Practice
            DTSTART:20261112T150000
            DTEND:20261112T160000
            RRULE:FREQ=WEEKLY;BYDAY=TU,TH;UNTIL=20261130
            END:VEVENT
        """.trimIndent()
        val events = parseICalendar(text)
        assertEquals(1, events.size)
        val occ = expandICalEvents(events, "f", "Team", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        // Thu 12 (start), Tue 17, Thu 19, Tue 24, Thu 26. Mon/Tue-before-start excluded.
        assertEquals(
            listOf(12, 17, 19, 24, 26).map { LocalDate.of(2026, 11, it) },
            occ.map { it.date },
        )
    }

    @Test fun honorsExdate() {
        val text = """
            BEGIN:VEVENT
            UID:e1
            SUMMARY:Daily
            DTSTART:20261110T090000Z
            RRULE:FREQ=DAILY;COUNT=3
            EXDATE:20261111T090000Z
            END:VEVENT
        """.trimIndent()
        val occ = expandICalEvents(
            parseICalendar(text), "f", "W",
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)),
            occ.map { it.date },
        )
    }

    @Test fun ignoresMonthlyRules() {
        val text = """
            BEGIN:VEVENT
            UID:m1
            SUMMARY:Monthly
            DTSTART:20261110T090000Z
            RRULE:FREQ=MONTHLY;COUNT=5
            END:VEVENT
        """.trimIndent()
        val events = parseICalendar(text)
        assertNull(events[0].recurrence)
        val occ = expandICalEvents(events, "f", "W", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(1, occ.size)
    }
}
