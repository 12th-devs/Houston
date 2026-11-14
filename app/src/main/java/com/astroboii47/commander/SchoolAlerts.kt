package com.astroboii47.commander

import android.content.Context
import java.time.Instant
import java.time.LocalDate

/**
 * School intelligence: grade-change detection, new/missing/upcoming
 * assignment tracking, configurable thresholds and class alerts.
 * Keeps history snapshots comparable only within the same courseId|term
 * and never presents computed values as official grades.
 */

data class SchoolAlertPrefs(
    val gradeBelowThreshold: Double? = null, // e.g. 70.0 → alert when course < threshold
    val dropThreshold: Double = 2.0, // percentage points
    val improvementThreshold: Double = 2.0,
    val upcomingDays: Long = 3,
    val notifyMissing: Boolean = true,
    val notifyNewAssignment: Boolean = true,
    val notifyUpcoming: Boolean = true,
    val notifyImproved: Boolean = true,
    val mutedCourseIds: Set<String> = emptySet(),
)

object SchoolAlertStore {
    private const val PREFS = "school_alerts"
    fun load(context: Context): SchoolAlertPrefs {
        val p = SettingsStore.prefs(context, PREFS)
        return SchoolAlertPrefs(
            gradeBelowThreshold = p.getString("below", null)?.toDoubleOrNull(),
            dropThreshold = p.getString("drop", null)?.toDoubleOrNull() ?: 2.0,
            improvementThreshold = p.getString("improve", null)?.toDoubleOrNull() ?: 2.0,
            upcomingDays = p.getString("days", null)?.toLongOrNull() ?: 3,
            notifyMissing = p.getBoolean("missing", true),
            notifyNewAssignment = p.getBoolean("new", true),
            notifyUpcoming = p.getBoolean("upcoming", true),
            notifyImproved = p.getBoolean("improved", true),
            mutedCourseIds = p.getString("muted", "").orEmpty().split('|').filter { it.isNotBlank() }.toSet(),
        )
    }

    fun save(context: Context, prefs: SchoolAlertPrefs) {
        SettingsStore.edit(context, PREFS) {
            if (prefs.gradeBelowThreshold == null) remove("below") else putString("below", prefs.gradeBelowThreshold.toString())
            putString("drop", prefs.dropThreshold.toString())
            putString("improve", prefs.improvementThreshold.toString())
            putString("days", prefs.upcomingDays.toString())
            putBoolean("missing", prefs.notifyMissing)
            putBoolean("new", prefs.notifyNewAssignment)
            putBoolean("upcoming", prefs.notifyUpcoming)
            putBoolean("improved", prefs.notifyImproved)
            putString("muted", prefs.mutedCourseIds.joinToString("|"))
        }
    }
}

/** Assignment identity for new-posted detection (stable across snapshots). */
fun assignmentKey(a: PsAssignment): String =
    "${a.courseId}|${a.dueIso ?: "no-date"}|${(a.name ?: "").lowercase()}|${(a.category ?: "").lowercase()}"

/**
 * Compares two snapshots and emits stable, dedupe-safe insights.
 * [prevCourses] and [curCourses] must use compatible grading periods —
 * callers filter to the same term first.
 */
fun detectSchoolChanges(
    prevCourses: List<PsCourse>,
    curCourses: List<PsCourse>,
    prefs: SchoolAlertPrefs,
    prevAssignments: Set<String> = prevCourses.flatMap { it.assignments }.map(::assignmentKey).toSet(),
    today: LocalDate = LocalDate.now(),
    nowIso: String = Instant.now().toString(),
): List<Insight> {
    val out = mutableListOf<Insight>()
    val prevByKey = prevCourses.associateBy { "${it.id}|${it.term}" }
    for (c in curCourses) {
        if (c.id in prefs.mutedCourseIds) continue
        val was = prevByKey["${c.id}|${c.term}"] ?: continue
        val old = was.gradePercent
        val now = c.gradePercent
        if (old != null && now != null) {
            val delta = now - old
            if (delta <= -prefs.dropThreshold) {
                out.add(
                    Insight(
                        id = "school.grade_changed:${c.id}|${c.term}:$now",
                        kind = InsightKind.SCHOOL,
                        title = "${c.name} dropped ${"%.1f".format(-delta)} pts",
                        explanation = "Previous: ${"%.1f".format(old)}% Current: ${"%.1f".format(now)}% " +
                            "Change: ${"%.1f".format(delta)} percentage points. Portal grade for ${c.term}.",
                        atIso = nowIso,
                        refs = listOf("school:${c.id}"),
                        score = 85,
                        actionLabel = "Open class",
                        actionTarget = "school:${c.id}",
                    ),
                )
            } else if (delta >= prefs.improvementThreshold && prefs.notifyImproved) {
                out.add(
                    Insight(
                        id = "school.grade_improved:${c.id}|${c.term}:$now",
                        kind = InsightKind.SCHOOL,
                        title = "${c.name} improved +${"%.1f".format(delta)} pts",
                        explanation = "Previous: ${"%.1f".format(old)}% Current: ${"%.1f".format(now)}%.",
                        atIso = nowIso,
                        refs = listOf("school:${c.id}"),
                        score = 60,
                        actionLabel = "Open class",
                        actionTarget = "school:${c.id}",
                    ),
                )
            }
        }
        if (prefs.gradeBelowThreshold != null && now != null && now < prefs.gradeBelowThreshold) {
            out.add(
                Insight(
                    id = "school.below_threshold:${c.id}|${c.term}:$now",
                    kind = InsightKind.SCHOOL,
                    title = "${c.name} below ${"%.0f".format(prefs.gradeBelowThreshold)}%",
                    explanation = "Current portal grade ${"%.1f".format(now)}% is under your threshold.",
                    atIso = nowIso,
                    refs = listOf("school:${c.id}"),
                    score = 90,
                    actionLabel = "Open class",
                    actionTarget = "school:${c.id}",
                ),
            )
        }
    }
    val curAssign = curCourses.flatMap { it.assignments }
    // New assignments.
    if (prefs.notifyNewAssignment) {
        for (a in curAssign) {
            if (assignmentKey(a) !in prevAssignments && a.id.isNotBlank()) {
                out.add(
                    Insight(
                        id = "school.assignment_posted:${a.id}",
                        kind = InsightKind.SCHOOL,
                        title = "New: ${a.name ?: "Assignment"} (${a.courseName})",
                        explanation = "Posted in ${a.courseName}${a.dueMdy?.let { " · due $it" } ?: ""}.",
                        atIso = nowIso,
                        refs = listOf("school:${a.courseId}", "assignment:${a.id}"),
                        score = 65,
                        actionLabel = "Open class",
                        actionTarget = "school:${a.courseId}",
                    ),
                )
            }
        }
    }
    // Missing.
    if (prefs.notifyMissing) {
        for (a in curAssign.filter { it.status == "missing" }) {
            out.add(
                Insight(
                    id = "school.assignment_missing:${a.id}",
                    kind = InsightKind.SCHOOL,
                    title = "Missing: ${a.name ?: "Assignment"} (${a.courseName})",
                    explanation = "Marked missing${a.dueMdy?.let { " · was due $it" } ?: ""}.",
                    atIso = nowIso,
                    refs = listOf("school:${a.courseId}", "assignment:${a.id}"),
                    score = 95,
                    actionLabel = "Open class",
                    actionTarget = "school:${a.courseId}",
                ),
            )
        }
    }
    // Due soon.
    if (prefs.notifyUpcoming) {
        for (a in curAssign) {
            val due = a.dueIso?.let { psIsoToLocalDate(it) } ?: continue
            if (a.status == "missing" || a.status == "graded" || a.status == "submitted") continue
            val inWindow = !due.isBefore(today) && !due.isAfter(today.plusDays(prefs.upcomingDays))
            if (inWindow) {
                out.add(
                    Insight(
                        id = "school.assignment_due:${a.id}",
                        kind = InsightKind.SCHOOL,
                        title = "Due ${relDayLabel(due, today)}: ${a.name ?: "Assignment"}",
                        explanation = "${a.courseName} · due ${a.dueMdy ?: due.toString()}.",
                        atIso = nowIso,
                        refs = listOf("school:${a.courseId}", "assignment:${a.id}"),
                        score = if (due == today || due == today.plusDays(1)) 88 else 55,
                        actionLabel = "Open class",
                        actionTarget = "school:${a.courseId}",
                    ),
                )
            }
        }
    }
    return out
}
