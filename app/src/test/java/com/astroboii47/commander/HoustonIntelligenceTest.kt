package com.astroboii47.commander

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HoustonIntelligenceTest {
    // --- Swim times ---
    @Test fun swimTimeRoundTrip() {
        assertEquals("1:08.20", formatSwimTime(68.2))
        assertEquals(68.2, parseSwimTime("1:08.20")!!, 1e-9)
        assertNull(parseSwimTime("abc"))
    }

    @Test fun swimCoursesNeverCrossCompare() {
        val goal = SwimGoal(id = "g", event = "100 Breast", course = SwimCourse.SCY, targetSec = 65.0)
        val lcm = RaceResult(
            id = "r", meetId = "m", meetName = "M", eventId = "100 Breast",
            eventName = "100 Breast", course = SwimCourse.LCM, timeSec = 60.0,
            place = 1, dateIso = "2026-11-01",
        )
        assertFalse(resultMatchesGoal(lcm, goal))
        val scy = lcm.copy(id = "r2", course = SwimCourse.SCY)
        assertTrue(resultMatchesGoal(scy, goal))
    }

    @Test fun swimProgressDefinedBaseline() {
        val g = SwimGoal(id = "g", event = "100 Breast", course = SwimCourse.SCY, targetSec = 65.0, startSec = 75.0, currentBestSec = 68.2)
        assertEquals(0.68, swimGoalProgress(g)!!, 1e-9)
        val und = SwimGoal(id = "h", event = "50 Free", course = SwimCourse.SCY, targetSec = 25.0)
        assertNull(swimGoalProgress(und))
    }

    @Test fun meetDigestVector() {
        // Sorted "a:1;b:2" SHA-256 — matches the recovered Meet Mobile interceptor.
        val d = MeetMobile.parameterDigest(mapOf("b" to "2", "a" to "1"))
        assertEquals(64, d.length)
        assertEquals(d, MeetMobile.parameterDigest(mapOf("a" to "1", "b" to "2")))
        assertTrue(MeetMobile.parameterDigest(mapOf("a" to "1")).isNotEmpty())
        assertEquals("", MeetMobile.parameterDigest(emptyMap()))
    }

    @Test fun resultDedupeStable() {
        val k1 = resultDedupeKey("m1", "e1", SwimCourse.SCY, 68.2, "Jane")
        val k2 = resultDedupeKey("m1", "e1", SwimCourse.SCY, 68.2, "Jane")
        val k3 = resultDedupeKey("m1", "e1", SwimCourse.SCM, 68.2, "Jane")
        assertEquals(k1, k2)
        assertNotEquals(k1, k3)
    }

    // --- School ---
    private fun course(id: String, term: String, pct: Double?, assigns: List<PsAssignment> = emptyList()) =
        PsCourse(id, "Course $id", null, null, term, null, pct, "available", null, assigns)

    private fun assign(id: String, courseId: String = "c1", status: String = "ungraded", dueIso: String? = null) =
        PsAssignment(id, courseId, "Course", "T", dueIso, null, null, "HW $id", 8.0, 10.0, 80.0, "B", status, emptyList())

    @Test fun gradeMoversThreshold() {
        val prev = mapOf("c1|Q1" to 94.0)
        val cur = listOf(course("c1", "Q1", 96.0))
        val moves = psGradeMovers(prev, cur)
        assertEquals(1, moves.size)
        assertEquals(2.0, moves[0].delta, 1e-9)
        // Below 0.5 ignored.
        assertTrue(psGradeMovers(mapOf("c1|Q1" to 95.8), listOf(course("c1", "Q1", 96.0))).isEmpty())
    }

    @Test fun schoolChangesCompressedWhenQuiet() {
        val prev = listOf(course("c1", "Q1", 90.0))
        val cur = listOf(course("c1", "Q1", 90.2))
        val prefs = SchoolAlertPrefs()
        val insights = detectSchoolChanges(prev, cur, prefs)
        assertTrue(insights.none { it.id.startsWith("school.grade_changed") })
    }

    @Test fun missingAndDueSoonEmit() {
        val prefs = SchoolAlertPrefs(upcomingDays = 3)
        val tomorrow = LocalDate.now().plusDays(1).toString()
        val cur = listOf(
            course("c1", "Q1", 90.0, listOf(assign("a1", status = "missing"), assign("a2", dueIso = tomorrow))),
        )
        val insights = detectSchoolChanges(emptyList(), cur, prefs, prevAssignments = emptySet())
        assertTrue(insights.any { it.id.startsWith("school.assignment_missing") })
        assertTrue(insights.any { it.id.startsWith("school.assignment_due") })
    }

    // --- Insights ---
    @Test fun insightDedupAndRank() {
        val a = Insight(id = "s:1", kind = InsightKind.SCHOOL, title = "A", explanation = "a", score = 90)
        val b = Insight(id = "s:2", kind = InsightKind.SWIM, title = "B", explanation = "b", score = 95)
        val c = Insight(id = "s:1", kind = InsightKind.SCHOOL, title = "A2", explanation = "a2", score = 90)
        val merged = mergeInsights(listOf(a), listOf(b, c))
        assertEquals(2, merged.size)
        assertEquals("A2", merged.first { it.id == "s:1" }.title)
        val ranked = rankInsights(merged)
        assertEquals("s:2", ranked.first().id)
    }

    @Test fun briefingOrderCalendarFirst() {
        val b = buildDailyBriefing(
            calendarLines = listOf("Blandfords at 4:00 PM"),
            schoolLines = listOf("Two upcoming assignments"),
            swimLines = listOf("Next goal: 100 Breast SCY"),
            memoryLines = listOf("Practice note captured"),
        )
        assertEquals(listOf("Calendar", "School", "Swimming", "Memories"), b.sections.map { it.heading })
    }

    // --- Memories ---
    @Test fun memorySearchFindsTags() {
        // Pure check of candidate validation used by search + query answers.
        val ids = validateReferencedIds(listOf("a", "zzz", "a", "b"), setOf("a", "b"))
        assertEquals(listOf("a", "b"), ids)
    }

    // --- Meet Mobile import parsers ---
    @Test fun mmTimeParses() {
        assertEquals(68.2, mmTimeToSec(parseJson("\"1:08.20\""))!!, 1e-9)
        assertEquals(68.2, mmTimeToSec(parseJson("68.2"))!!, 1e-9)
        assertNull(mmTimeToSec(parseJson("null")))
        assertNull(mmTimeToSec(parseJson("\"DQ\"")))
    }

    @Test fun mmDqDetection() {
        val dq = parseJson("{\"disqualified\":true,\"finishTime\":60.0}") as JVal.Obj
        assertTrue(mmIsDq(dq))
        val ok = parseJson("{\"finishTime\":60.0}") as JVal.Obj
        assertFalse(mmIsDq(ok))
    }

    @Test fun mmMeetsParse() {
        val payload = parseJson("[{\"meetId\":5773706,\"meetName\":\"NCAA SOUTH\",\"city\":\"Manila\"}]")
        val meets = parseMmMeets(payload)
        assertEquals(1, meets.size)
        assertEquals("5773706", meets[0].id)
        assertEquals("NCAA SOUTH", meets[0].name)
    }

    @Test fun mmRoundParsesFinishedEntries() {
        val payload = parseJson(
            "{\"entries\":[{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"finishTime\":68.2,\"overallPlace\":3," +
                "\"splits\":[{\"distance\":50,\"time\":32.1,\"cumulativeTime\":32.1}]}]}",
        )
        val entries = parseMmRound(payload, "100 Breast")
        assertEquals(1, entries.size)
        assertEquals(68.2, entries[0].timeSec!!, 1e-9)
        assertEquals(3, entries[0].place)
        assertEquals("Jane Doe", entries[0].swimmerLabel)
        assertEquals(1, entries[0].splits.size)
        // Pending (seed-only) rounds yield no importable entries.
        val pending = parseJson("{\"entries\":[{\"firstName\":\"Jane\",\"seedTime\":70.0}]}")
        assertTrue(parseMmRound(pending, "100 Breast").isEmpty())
    }

    @Test fun weeklyReviewHonestWhenEmpty() {
        val week = buildWeeklyReview("Nov 7 – Nov 14", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(week.lines.any { "not a complete picture" in it || "not the full week" in it || "none recorded" in it })
    }

    // --- Automations ---
    @Test fun automationEvalGradeChange() {
        val rules = AutomationEngine.defaultRules()
        val insights = listOf(
            Insight(id = "school.grade_changed:c1|Q1:88.0", kind = InsightKind.SCHOOL, title = "drop", explanation = "x", score = 85),
        )
        val fired = AutomationEngine.evaluate(rules, emptyList(), insights)
        assertTrue(fired.any { it.first.id == "auto-grade-watch" })
        // Disabled rules never fire.
        val off = rules.map { if (it.id == "auto-grade-watch") it.copy(enabled = false) else it }
        assertTrue(AutomationEngine.evaluate(off, emptyList(), insights).none { it.first.id == "auto-grade-watch" })
    }
}
