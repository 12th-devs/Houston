package com.astroboii47.commander

import android.content.Context
import java.io.File
import java.time.LocalDate

/** On-device PowerSchool state. Passwords are never persisted: only the
 *  portal session cookies (private internal file) plus the cached grade
 *  snapshot, mirroring the desktop project's privacy model. */

object PowerSchoolStore {
    private const val PREFS = "powerschool"
    private const val KEY_USERNAME = "username"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_REFRESHED_AT = "refreshed_at"
    private const val KEY_SELECTED_TERM = "selected_term"

    fun prefs(context: Context) = SettingsStore.prefs(context, PREFS)

    fun username(context: Context): String? =
        SettingsStore.getString(context, PREFS, KEY_USERNAME)?.ifEmpty { null }

    fun setUsername(context: Context, value: String) =
        SettingsStore.edit(context, PREFS) { putString(KEY_USERNAME, value) }

    fun baseUrl(context: Context): String =
        SettingsStore.getString(context, PREFS, KEY_BASE_URL)?.ifBlank { null }
            ?: PS_DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, value: String) =
        SettingsStore.edit(context, PREFS) { putString(KEY_BASE_URL, value.trim().trimEnd('/')) }

    fun refreshedAt(context: Context): String? =
        SettingsStore.getString(context, PREFS, KEY_REFRESHED_AT)

    fun setRefreshedAt(context: Context, value: String) =
        SettingsStore.edit(context, PREFS) { putString(KEY_REFRESHED_AT, value) }

    fun selectedTerm(context: Context): String? =
        SettingsStore.getString(context, PREFS, KEY_SELECTED_TERM)

    fun setSelectedTerm(context: Context, term: String) =
        SettingsStore.edit(context, PREFS) { putString(KEY_SELECTED_TERM, term) }

    fun clearAccount(context: Context) {
        SettingsStore.edit(context, PREFS) {
            remove(KEY_USERNAME); remove(KEY_REFRESHED_AT); remove(KEY_SELECTED_TERM)
        }
        File(context.filesDir, "powerschool").let { dir ->
            File(dir, "session.json").delete()
            File(dir, "records.json").delete()
        }
    }

    fun client(context: Context): PowerSchoolClient =
        PowerSchoolClient(baseUrl(context), context.filesDir, username(context))

    fun saveCourses(context: Context, courses: List<PsCourse>, refreshedAt: String) {
        val dir = File(context.filesDir, "powerschool").apply { mkdirs() }
        val tmp = File(dir, "records.json.tmp")
        tmp.writeText(psCoursesToJson(courses))
        val dst = File(dir, "records.json")
        if (!tmp.renameTo(dst)) {
            dst.delete()
            tmp.renameTo(dst)
        }
        setRefreshedAt(context, refreshedAt)
    }

    fun loadCourses(context: Context): List<PsCourse> {
        val f = File(File(context.filesDir, "powerschool"), "records.json")
        if (!f.exists()) return emptyList()
        return runCatching { psCoursesFromJson(f.readText()).sortedWith(compareBy({ it.name.lowercase() }, { termOrder(it.term) })) }
            .getOrDefault(emptyList())
    }

    fun hasSession(context: Context): Boolean = client(context).loadSession().also {
        // loadSession has side effects on a throwaway client only.
    }

    /**
     * One row per real assignment. Quarter and semester records overlap (the
     * portal serves the same assignment under both terms), so dedupe across
     * courses exactly like the desktop `selectAssignments` (course|due|name|
     * category), preferring the quarter-term record, then the one carrying a
     * score.
     */
    fun distinctAssignments(courses: List<PsCourse>): List<PsAssignment> {
        val byKey = LinkedHashMap<String, PsAssignment>()
        for (c in courses) for (a in c.assignments) {
            val key = "${a.courseId}|${a.dueIso}|${a.name}|${a.category}"
            val cur = byKey[key]
            byKey[key] = if (cur == null) a else preferAssignment(cur, a)
        }
        return byKey.values.toList()
    }

    private fun preferAssignment(a: PsAssignment, b: PsAssignment): PsAssignment {
        val aq = a.term.startsWith("Q")
        val bq = b.term.startsWith("Q")
        if (aq != bq) return if (aq) a else b
        val aScored = a.earned != null || a.percent != null
        val bScored = b.earned != null || b.percent != null
        if (aScored != bScored) return if (aScored) a else b
        return a
    }

    /** Assignments due on [day]. */
    fun dueOn(courses: List<PsCourse>, day: LocalDate): List<PsAssignment> =
        distinctAssignments(courses)
            .filter { a -> a.dueIso?.let { psIsoToLocalDate(it) } == day }
            .sortedWith(compareBy({ it.status == "missing" }, { it.courseName.lowercase() }))

    fun upcoming(courses: List<PsCourse>, today: LocalDate, days: Long = 14): List<PsAssignment> =
        distinctAssignments(courses)
            .filter { a ->
                val due = a.dueIso?.let { psIsoToLocalDate(it) }
                a.status == "missing" || (due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(days)))
            }
            .sortedWith(compareBy({ it.status != "missing" }, { it.dueIso ?: "9999" }, { it.courseName.lowercase() }))
            .take(60)
}
