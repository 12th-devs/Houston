package com.astroboii47.commander

import android.content.Context
import java.time.Instant
import java.time.LocalDate

/**
 * Central proactive-intelligence pipeline for Houston 3.0.
 *
 * Events are structured strings (e.g. `school.grade_changed`,
 * `swim.personal_best`, `memory.created`). Insights carry a STABLE id so
 * repeated syncs never duplicate them, plus expiry + read/ack state.
 * Ranking is deterministic: priority score first, then newest timestamp,
 * then id. AI use is optional and always labeled; this core never invents
 * facts — every insight cites real source refs.
 */

data class InsightEvent(
    val type: String,
    val atIso: String = Instant.now().toString(),
    val refs: List<String> = emptyList(),
    val payload: Map<String, String> = emptyMap(),
)

enum class InsightKind {
    CALENDAR, SCHOOL, SWIM, MEMORY, BRIEFING, AUTOMATION, WEEKLY,
}

data class Insight(
    val id: String, // stable: "<type>:<key>"
    val kind: InsightKind,
    val title: String,
    val explanation: String,
    val atIso: String = Instant.now().toString(),
    val refs: List<String> = emptyList(),
    val score: Int = 50, // 0..100 priority
    val expiresIso: String? = null,
    val read: Boolean = false,
    val acked: Boolean = false,
    val actionLabel: String? = null,
    val actionTarget: String? = null, // deep-link hint: "school:<courseId>" etc.
)

/** Deterministic rank: score desc, then newest, then id. */
fun rankInsights(insights: List<Insight>, nowIso: String = Instant.now().toString()): List<Insight> {
    val live = insights.filter { it.expiresIso == null || it.expiresIso >= nowIso }
    return live.sortedWith(compareByDescending<Insight> { it.score }.thenByDescending { it.atIso }.thenBy { it.id })
}

/** Merge new insights into existing: same id replaces, acked state preserved. */
fun mergeInsights(existing: List<Insight>, incoming: List<Insight>): List<Insight> {
    val byId = existing.associateBy { it.id }.toMutableMap()
    for (n in incoming) {
        val old = byId[n.id]
        byId[n.id] = if (old != null && old.acked) n.copy(acked = true, read = true) else n
    }
    return byId.values.sortedBy { it.id }
}

data class BriefingSection(val heading: String, val lines: List<String>)

data class DailyBriefing(
    val dateIso: String,
    val greeting: String,
    val sections: List<BriefingSection>,
    val generatedAtIso: String = Instant.now().toString(),
)

/**
 * Builds the contextual daily briefing in the required default order:
 * Calendar → School → Swimming → Memories → Other. Every line must come
 * from connected/local data passed in — this function never fabricates.
 */
fun buildDailyBriefing(
    today: LocalDate = LocalDate.now(),
    calendarLines: List<String> = emptyList(),
    schoolLines: List<String> = emptyList(),
    swimLines: List<String> = emptyList(),
    memoryLines: List<String> = emptyList(),
    otherLines: List<String> = emptyList(),
): DailyBriefing {
    val sections = mutableListOf<BriefingSection>()
    if (calendarLines.isNotEmpty()) sections.add(BriefingSection("Calendar", calendarLines))
    if (schoolLines.isNotEmpty()) sections.add(BriefingSection("School", schoolLines))
    if (swimLines.isNotEmpty()) sections.add(BriefingSection("Swimming", swimLines))
    if (memoryLines.isNotEmpty()) sections.add(BriefingSection("Memories", memoryLines))
    if (otherLines.isNotEmpty()) sections.add(BriefingSection("More", otherLines))
    if (sections.isEmpty()) {
        sections.add(
            BriefingSection(
                "Today",
                listOf("Nothing urgent. Connect a calendar, sync PowerSchool, or log a swim to fill this in."),
            ),
        )
    }
    return DailyBriefing(
        dateIso = today.toString(),
        greeting = "Your Day — " + today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")),
        sections = sections,
    )
}

fun buildWeeklyReview(
    weekLabel: String,
    calendarHighlights: List<String>,
    assignmentActivity: List<String>,
    gradeChanges: List<String>,
    swimResults: List<String>,
    practiceVolume: List<String>,
    memoryHighlights: List<String>,
): BriefingSection {
    val lines = mutableListOf<String>()
    fun add(head: String, items: List<String>) {
        if (items.isEmpty()) lines.add("$head: none recorded — partial tracking is not the full week.")
        else {
            lines.add(head + ":")
            items.take(5).forEach { lines.add("• $it") }
        }
    }
    add("Calendar", calendarHighlights)
    add("Assignments", assignmentActivity)
    add("Grades", gradeChanges)
    add("Swimming", swimResults + practiceVolume)
    add("Memories", memoryHighlights)
    return BriefingSection("Week of $weekLabel", lines)
}

object InsightStore {
    private const val PREFS = "houston_insights"
    private const val KEY_INSIGHTS = "insights_json"

    fun load(context: Context): List<Insight> {
        val raw = SettingsStore.getString(context, PREFS, KEY_INSIGHTS) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching { insightsFromJson(raw) }.getOrDefault(emptyList())
    }

    fun save(context: Context, insights: List<Insight>) {
        // Cap history: keep unacked + 100 most recent acked.
        val trimmed = (insights.filter { !it.acked } +
            insights.filter { it.acked }.sortedByDescending { it.atIso }.take(100))
            .distinctBy { it.id }
        SettingsStore.edit(context, PREFS) { putString(KEY_INSIGHTS, insightsToJson(trimmed)) }
    }

    /** Idempotent ingest: dedupes by stable id, preserves ack. Returns new arrivals. */
    fun ingest(context: Context, incoming: List<Insight>): List<Insight> {
        val existing = load(context)
        val known = existing.associateBy { it.id }
        val merged = mergeInsights(existing, incoming)
        save(context, merged)
        return incoming.filter { known[it.id] == null }
    }

    fun markRead(context: Context, id: String) {
        save(context, load(context).map { if (it.id == id) it.copy(read = true) else it })
    }

    fun ack(context: Context, id: String) {
        save(context, load(context).map { if (it.id == id) it.copy(acked = true, read = true) else it })
    }

    fun pruneExpired(context: Context, nowIso: String = Instant.now().toString()) {
        save(context, load(context).filter { it.expiresIso == null || it.expiresIso >= nowIso || !it.acked })
    }
}

internal fun insightsToJson(list: List<Insight>): String = buildString {
    append('[')
    list.forEachIndexed { i, s ->
        if (i > 0) append(',')
        append("{\"id\":\"${escapeJson(s.id)}\",\"kind\":\"${s.kind}\",")
        append("\"title\":\"${escapeJson(s.title)}\",\"explanation\":\"${escapeJson(s.explanation)}\",")
        append("\"at\":\"${escapeJson(s.atIso)}\",\"score\":${s.score},")
        append("\"read\":${s.read},\"acked\":${s.acked},")
        append("\"expires\":${s.expiresIso?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        append("\"actionLabel\":${s.actionLabel?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        append("\"actionTarget\":${s.actionTarget?.let { "\"${escapeJson(it)}\"" } ?: "null"},\"refs\":[")
        s.refs.forEachIndexed { j, r ->
            if (j > 0) append(',')
            append("\"${escapeJson(r)}\"")
        }
        append("]}")
    }
    append(']')
}

internal fun insightsFromJson(text: String): List<Insight> {
    val root = parseJson(text)
    val arr = root.arr() ?: return emptyList()
    return arr.mapNotNull { v ->
        if (v !is JVal.Obj) return@mapNotNull null
        val id = v.objStr("id") ?: return@mapNotNull null
        Insight(
            id = id,
            kind = runCatching { InsightKind.valueOf(v.objStr("kind") ?: "SCHOOL") }.getOrDefault(InsightKind.SCHOOL),
            title = v.objStr("title") ?: id,
            explanation = v.objStr("explanation") ?: "",
            atIso = v.objStr("at") ?: Instant.now().toString(),
            refs = v.objArr("refs")?.mapNotNull { (it as? JVal.Str)?.s } ?: emptyList(),
            score = (v.obj("score")?.num()?.toInt()) ?: 50,
            expiresIso = v.objStr("expires"),
            read = v.objBool("read") == true,
            acked = v.objBool("acked") == true,
            actionLabel = v.objStr("actionLabel"),
            actionTarget = v.objStr("actionTarget"),
        )
    }
}
