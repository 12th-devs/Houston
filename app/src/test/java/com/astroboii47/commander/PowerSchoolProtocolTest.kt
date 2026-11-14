package com.astroboii47.commander

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PowerSchoolProtocolTest {
    @Test fun trimsNumbersLikeDesktop() {
        assertEquals("100", psTrimNum("100.00"))
        assertEquals("95.5", psTrimNum("95.5000"))
        assertEquals("", psTrimNum(null))
        assertEquals("", psTrimNum("abc"))
    }

    @Test fun dateHelpersRoundTrip() {
        assertEquals("2026-8-4", mdyToXteDate("08/04/2026"))
        assertEquals("08/04/2026", isoToMdy("2026-08-04"))
        assertEquals("2026-08-04", isoFromXte("2026-8-4"))
        assertEquals(LocalDate.of(2026, 8, 4), psMdyToLocalDate("08/04/2026"))
        assertNull(psMdyToLocalDate("13/40/2026"))
        assertEquals(95.0, psNumberValue("95%"))
        assertNull(psNumberValue("A 100"))
        assertNull(psNumberValue("--"))
    }

    @Test fun loginFormRoundTrip() {
        val html = """
            <html><body><form id="LoginForm" action="/guardian/home.html" method="post">
            <input type="hidden" name="dbpw" value="">
            <input type="hidden" name="translatorpw" value="en;">
            <input type="hidden" name="translator_username" value="">
            <input type="hidden" name="translator_password" value="">
            <input type="text" name="account" value="">
            <input type="password" name="pw" value="">
            </form></body></html>
        """.trimIndent()
        val form = parseLoginForm(html)
        assertEquals("/guardian/home.html", form.action)
        val payload = buildLoginPayload(form.fields, "parent@example.com", "s3cret")
        assertEquals("s3cret", payload["pw"])
        assertEquals("s3cret", payload["dbpw"])
        assertEquals("parent@example.com", payload["account"])
        assertEquals("en", payload["translator_username"])
    }

    @Test fun detectsLoginAndChallengePages() {
        assertTrue(psIsLoginHtml("<html>Parent, Guardian, and Student Login<form id=LoginForm></form></html>"))
        assertTrue(psIsLoginHtml("<input id=\"fieldPassword\" type=password name=pw>"))
        assertFalse(psIsLoginHtml("<button onclick=\"switchStudent(\">x</button><div>grades</div>"))
        assertFalse(psIsLoginHtml("<html><body>grades table, password policy text</body></html>"))
        assertTrue(psHasMfaChallenge("Please enter the verification code sent to you"))
        assertFalse(psHasMfaChallenge("Welcome to PowerSchool"))
        assertTrue(psLoginRejected("<div class=\"feedback-alert\">Invalid username or password</div>"))
        assertFalse(psLoginRejected("<div>Grades and Attendance</div>"))
    }

    private fun homeFixture(): String = """
        <html><body><table>
        <tr><td>1(A)</td>
        <td><a href="scores.html?frn=001234">Geometry<br>Geometry details</a><br>
        <a href="mailto:teacher@school.edu">Email Jane Doe</a></td>
        <td><a href="scores.html?frn=001234&fg=Q2&begdate=10/01/2026&enddate=12/31/2026&schoolid=9">A 95</a></td>
        <td><a href="scores.html?frn=001234&fg=S1&begdate=08/01/2026&enddate=12/31/2026&schoolid=9">[ i ]</a></td>
        </tr>
        <tr><td>2(B)</td>
        <td><a href="scores.html?frn=009999">Biology<br>Bio</a></td>
        <td><a href="scores.html?frn=009999&fg=Q2">B+ 88</a></td>
        <td><a href="scores.html?frn=009999&fg=Q2">B+ 88</a></td>
        </tr>
        </table></body></html>
    """.trimIndent()

    @Test fun parsesHomePageLikeDesktop() {
        val entries = parseHomePage(homeFixture())
        // Duplicate Q2 Biology link dedupes to 3 entries.
        assertEquals(3, entries.size)
        val geo = entries.first { it.courseId == "001234" && it.termCode == "Q2" }
        assertEquals("Geometry", geo.courseName)
        assertEquals("Jane Doe", geo.teacher)
        assertEquals("1(A)", geo.expression)
        assertEquals("/guardian/scores.html?frn=001234&fg=Q2&begdate=10/01/2026&enddate=12/31/2026&schoolid=9", geo.scoresUrl)
        assertEquals("A 95%", geo.gradeText)
        assertEquals("10/01/2026", geo.beginDate)
        val incomplete = entries.first { it.termCode == "S1" }
        assertNull(incomplete.gradeText)
        val bio = entries.first { it.courseId == "009999" }
        assertEquals("Biology", bio.courseName)
        assertEquals("B+ 88%", bio.gradeText)
    }

    @Test fun parsesScoresMetadata() {
        val html = "<div data-sectionid=\"0045678\"></div><script>var studentFRN = '00198765';</script>"
        val (section, student) = parseScoresMetadata(html)
        assertEquals("45678", section)
        // Desktop strips one leading "001", like powerschool-go.
        assertEquals("98765", student)
    }

    private fun xteFixture(): String = """
        [{
          "assignmentid": 832813,
          "_assignmentsections": [{
            "name": "Course Overview Form",
            "duedate": "2026-08-04T00:00:00",
            "scoreentrypoints": 100,
            "iscountedinfinalgrade": true,
            "_assignmentcategoryassociations": [
              {"isprimary": false, "_teachercategory": {"name": "Other"}},
              {"isprimary": true, "_teachercategory": {"name": "Assignments"}}
            ],
            "_assignmentscores": [{
              "isexempt": false, "ismissing": false, "isincomplete": false, "isabsent": false,
              "islate": false, "iscollected": true,
              "scorepoints": 100, "actualscoreentered": "100", "scorepercent": 100, "scorelettergrade": "A"
            }]
          }]
        },
        {
          "assignmentid": 900001,
          "_assignmentsections": [{
            "name": "Missing Homework",
            "duedate": "2026-09-01T00:00:00",
            "scoreentrypoints": 20,
            "iscountedinfinalgrade": true,
            "_assignmentcategoryassociations": [],
            "_assignmentscores": [{
              "isexempt": false, "ismissing": true, "isincomplete": false, "isabsent": false,
              "islate": false, "iscollected": false,
              "scorepoints": 0, "actualscoreentered": "", "scorepercent": 0, "scorelettergrade": ""
            }]
          }]
        }]
    """.trimIndent()

    @Test fun mapsXteLookupLikeDesktop() {
        val lookup = parseJson(xteFixture())
        val out = mapLookupToAssignments(lookup, "001234", "2026-08-01", "2026-12-31")
        assertEquals(2, out.size)
        val graded = out[0]
        assertEquals("832813", graded.assignmentId)
        assertEquals("08/04/2026", graded.dueDate)
        assertEquals("Assignments", graded.category)
        assertEquals("100/100", graded.score)
        assertEquals("100", graded.percent)
        assertEquals("A", graded.grade)
        // Desktop marks collected-but-ungraded work; parity matters more than tidiness.
        assertEquals(listOf("collected"), graded.flags)
        val missing = out[1]
        assertEquals(listOf("missing"), missing.flags)
        assertEquals("", missing.score)
        // Out-of-range rows are dropped.
        val filtered = mapLookupToAssignments(lookup, "001234", "2026-09-01", "2026-12-31")
        assertEquals(1, filtered.size)
        assertEquals("900001", filtered[0].assignmentId)
    }

    @Test fun normalizesGradesAndStatuses() {
        val raw = listOf(
            RawRecord(
                course = "Geometry (S1)", teacher = "Jane Doe", expression = "1(A)",
                termCode = "Q2", courseId = "001234", gradeText = "A 95%",
                beginDate = null, endDate = null, schoolId = null,
                assignments = listOf(
                    RawAssignment("1", "11/20/2026", "Homework", "HW 1", emptyList(), "18/20", "90", "A"),
                    RawAssignment("2", "11/10/2026", "Homework", "HW 0", listOf("missing"), "", "", ""),
                    RawAssignment("3", null, "Quiz", "Q", emptyList(), "", "", ""),
                ),
            ),
        )
        val courses = normalizeRawRecords(raw, LocalDate.of(2026, 11, 14))
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("Q2", c.term)
        assertEquals("A", c.gradeLetter)
        assertEquals(95.0, c.gradePercent)
        assertEquals("available", c.gradeStatus)
        val byName = c.assignments.associateBy { it.name }
        assertEquals("graded", byName["HW 1"]!!.status)
        assertEquals(18.0, byName["HW 1"]!!.earned)
        assertEquals(20.0, byName["HW 1"]!!.possible)
        assertEquals("missing", byName["HW 0"]!!.status)
        assertEquals("ungraded", byName["Q"]!!.status)
        assertTrue(byName["HW 1"]!!.id.isNotEmpty())
    }

    @Test fun dedupesAssignmentsAcrossTerms() {
        fun course(term: String, grade: String) = PsCourse(
            id = "1", name = "Geometry", teacher = null, expression = null, term = term,
            gradeLetter = "A", gradePercent = 95.0, gradeStatus = "available", gradeRaw = grade,
            assignments = listOf(
                PsAssignment(
                    id = "shared", courseId = "1", courseName = "Geometry", term = term,
                    dueIso = "2026-11-20", dueMdy = "11/20/2026", category = "HW",
                    name = "Essay", earned = 18.0, possible = 20.0, percent = 90.0,
                    letter = "A", status = "graded", flags = emptyList(),
                ),
                PsAssignment(
                    id = "only-$term", courseId = "1", courseName = "Geometry", term = term,
                    dueIso = "2026-11-21", dueMdy = "11/21/2026", category = "HW",
                    name = "Unique $term", earned = null, possible = null, percent = null,
                    letter = null, status = "ungraded", flags = emptyList(),
                ),
            ),
        )
        val out = PowerSchoolStore.distinctAssignments(listOf(course("S1", "A"), course("Q2", "A")))
        // Shared row collapses to the quarter-term record; uniques survive.
        assertEquals(3, out.size)
        assertEquals(1, out.count { it.name == "Essay" })
        assertEquals("Q2", out.first { it.name == "Essay" }.term)
    }

    @Test fun cacheJsonRoundTrips() {
        val courses = listOf(
            PsCourse(
                id = "1", name = "Geometry \"Honors\"", teacher = "O'Brien", expression = "1(A)",
                term = "Q2", gradeLetter = "A", gradePercent = 95.5, gradeStatus = "available",
                gradeRaw = "A 95.5%", assignments = listOf(
                    PsAssignment(
                        id = "a1", courseId = "1", courseName = "Geometry", term = "Q2",
                        dueIso = "2026-11-20", dueMdy = "11/20/2026", category = "HW",
                        name = "Essay\n1", earned = 18.0, possible = 20.0, percent = 90.0,
                        letter = "A", status = "graded", flags = listOf("collected"),
                    ),
                ),
            ),
        )
        val json = psCoursesToJson(courses)
        val back = psCoursesFromJson(json)
        assertEquals(1, back.size)
        assertEquals("Geometry \"Honors\"", back[0].name)
        assertEquals("O'Brien", back[0].teacher)
        assertEquals(95.5, back[0].gradePercent)
        assertEquals(1, back[0].assignments.size)
        assertEquals("Essay\n1", back[0].assignments[0].name)
        assertEquals("2026-11-20", back[0].assignments[0].dueIso)
        assertEquals(listOf("collected"), back[0].assignments[0].flags)
    }
}
