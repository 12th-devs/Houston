package com.astroboii47.commander

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Small, reliable automation engine. Template-based rules (no node graph):
 * trigger + conditions → actions, with execution history and
 * enable/disable. External/modifying actions require explicit user
 * confirmation at creation time ([requiresConfirm]).
 */

enum class AutoTrigger {
    SCHEDULED_TIME, CALENDAR_PROXIMITY, GRADE_CHANGE, ASSIGNMENT_DUE,
    NEW_RESULT, GOAL_COMPLETED, MEMORY_CREATED, MANUAL,
}

enum class AutoActionKind {
    GENERATE_BRIEFING, SEND_NOTIFICATION, SUGGEST_REMINDER,
    SAVE_MEMORY, OPEN_SCREEN, REFRESH_SOURCE, CREATE_INSIGHT, LAUNCH_INTEGRATION,
}

data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val trigger: AutoTrigger,
    val timeIso: String? = null, // "HH:mm" for SCHEDULED_TIME
    val gradeThreshold: Double? = null,
    val eventType: String? = null, // insight/event type filter
    val action: AutoActionKind,
    val actionParam: String = "",
    val requiresConfirm: Boolean = false,
)

data class AutomationRun(
    val ruleId: String,
    val atIso: String,
    val success: Boolean,
    val detail: String,
)

object AutomationEngine {
    private const val PREFS = "houston_automations"
    private const val KEY_RULES = "rules_json"
    private const val KEY_RUNS = "runs_json"

    fun defaultRules(): List<AutomationRule> = listOf(
        AutomationRule(
            id = "auto-morning-briefing", name = "Morning briefing",
            trigger = AutoTrigger.SCHEDULED_TIME, timeIso = "07:30",
            action = AutoActionKind.GENERATE_BRIEFING,
        ),
        AutomationRule(
            id = "auto-grade-watch", name = "Grade drops",
            trigger = AutoTrigger.GRADE_CHANGE, gradeThreshold = 2.0,
            action = AutoActionKind.SEND_NOTIFICATION, actionParam = "school",
        ),
        AutomationRule(
            id = "auto-due-soon", name = "Assignments due tomorrow",
            trigger = AutoTrigger.ASSIGNMENT_DUE,
            action = AutoActionKind.SEND_NOTIFICATION, actionParam = "school",
        ),
        AutomationRule(
            id = "auto-pb", name = "Celebrate personal bests",
            trigger = AutoTrigger.NEW_RESULT,
            action = AutoActionKind.SEND_NOTIFICATION, actionParam = "swim",
        ),
    )

    fun loadRules(context: Context): List<AutomationRule> {
        val raw = SettingsStore.getString(context, PREFS, KEY_RULES)
        if (raw.isNullOrBlank()) {
            val defaults = defaultRules()
            saveRules(context, defaults)
            return defaults
        }
        return runCatching { rulesFromJson(parseJson(raw)) }.getOrDefault(defaultRules())
    }

    fun saveRules(context: Context, rules: List<AutomationRule>) {
        SettingsStore.edit(context, PREFS) { putString(KEY_RULES, rulesToJson(rules)) }
    }

    fun loadRuns(context: Context): List<AutomationRun> {
        val raw = SettingsStore.getString(context, PREFS, KEY_RUNS) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            (parseJson(raw) as? JVal.Arr)?.l?.mapNotNull { e ->
                if (e !is JVal.Obj) return@mapNotNull null
                AutomationRun(
                    ruleId = e.objStr("r") ?: return@mapNotNull null,
                    atIso = e.objStr("t") ?: return@mapNotNull null,
                    success = e.objBool("s") == true,
                    detail = e.objStr("d").orEmpty(),
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun recordRun(context: Context, run: AutomationRun) {
        val runs = (listOf(run) + loadRuns(context)).take(100)
        val b = StringBuilder("[")
        runs.forEachIndexed { i, r ->
            if (i > 0) b.append(',')
            b.append("{\"r\":\"${escapeJson(r.ruleId)}\",\"t\":\"${escapeJson(r.atIso)}\",\"s\":${r.success},\"d\":\"${escapeJson(r.detail)}\"}")
        }
        b.append(']')
        SettingsStore.edit(context, PREFS) { putString(KEY_RUNS, b.toString()) }
    }

    /**
     * Evaluates enabled rules against the latest events/insights.
     * Pure decision step — side effects (notify/refresh) are performed by
     * the caller from the returned actions so tests stay deterministic.
     */
    fun evaluate(
        rules: List<AutomationRule>,
        events: List<InsightEvent>,
        insights: List<Insight>,
        today: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now(),
    ): List<Pair<AutomationRule, String>> {
        val out = mutableListOf<Pair<AutomationRule, String>>()
        for (rule in rules) {
            if (!rule.enabled) continue
            when (rule.trigger) {
                AutoTrigger.MANUAL -> Unit // only via explicit run
                AutoTrigger.SCHEDULED_TIME -> {
                    val want = runCatching { LocalTime.parse(rule.timeIso ?: "") }.getOrNull() ?: continue
                    if (want.hour == nowTime.hour && kotlin.math.abs(want.minute - nowTime.minute) <= 15) {
                        out.add(rule to "Scheduled for ${rule.timeIso}")
                    }
                }
                AutoTrigger.GRADE_CHANGE -> {
                    val hit = insights.any { it.kind == InsightKind.SCHOOL && it.id.startsWith("school.grade_changed") && !it.acked }
                    if (hit) out.add(rule to "Unacknowledged grade change")
                }
                AutoTrigger.ASSIGNMENT_DUE -> {
                    val hit = insights.any { it.kind == InsightKind.SCHOOL && it.id.startsWith("school.assignment_due") && !it.acked }
                    if (hit) out.add(rule to "Assignment due soon")
                }
                AutoTrigger.NEW_RESULT, AutoTrigger.GOAL_COMPLETED -> {
                    val hit = insights.any { it.kind == InsightKind.SWIM && !it.acked }
                    if (hit) out.add(rule to "Unacknowledged swim milestone")
                }
                AutoTrigger.MEMORY_CREATED -> {
                    if (events.any { it.type == "memory.created" }) out.add(rule to "Memory created")
                }
                AutoTrigger.CALENDAR_PROXIMITY -> {
                    if (events.any { it.type == "calendar.event_upcoming" }) out.add(rule to "Calendar event near")
                }
            }
        }
        return out
    }

    private fun rulesToJson(rules: List<AutomationRule>): String = buildString {
        append('[')
        rules.forEachIndexed { i, r ->
            if (i > 0) append(',')
            append("{\"id\":\"${escapeJson(r.id)}\",\"n\":\"${escapeJson(r.name)}\",\"e\":${r.enabled},")
            append("\"tr\":\"${r.trigger}\",\"ti\":${r.timeIso?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"gt\":${r.gradeThreshold?.toString() ?: "null"},\"ev\":${r.eventType?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"ac\":\"${r.action}\",\"ap\":\"${escapeJson(r.actionParam)}\",\"rc\":${r.requiresConfirm}}")
        }
        append(']')
    }

    private fun rulesFromJson(v: JVal): List<AutomationRule> {
        val arr = v.arr() ?: return emptyList()
        return arr.mapNotNull { e ->
            if (e !is JVal.Obj) return@mapNotNull null
            AutomationRule(
                id = e.objStr("id") ?: return@mapNotNull null,
                name = e.objStr("n") ?: "Rule",
                enabled = e.objBool("e") != false,
                trigger = runCatching { AutoTrigger.valueOf(e.objStr("tr") ?: "MANUAL") }.getOrDefault(AutoTrigger.MANUAL),
                timeIso = e.objStr("ti"),
                gradeThreshold = e.obj("gt")?.num(),
                eventType = e.objStr("ev"),
                action = runCatching { AutoActionKind.valueOf(e.objStr("ac") ?: "SEND_NOTIFICATION") }.getOrDefault(AutoActionKind.SEND_NOTIFICATION),
                actionParam = e.objStr("ap").orEmpty(),
                requiresConfirm = e.objBool("rc") == true,
            )
        }
    }
}

/** Notification channel ids + user prefs (granular, default on). */
object HoustonNotifyPrefs {
    const val CH_BRIEFING = "houston_briefing"
    const val CH_SCHOOL = "houston_school"
    const val CH_SWIM = "houston_swim"
    const val CH_MEMORY = "houston_memory"
    const val CH_AUTO = "houston_automation"

    private const val PREFS = "notify_prefs"
    fun enabled(context: Context, channel: String): Boolean =
        SettingsStore.prefs(context, PREFS).getBoolean(channel, true)

    fun setEnabled(context: Context, channel: String, on: Boolean) {
        SettingsStore.edit(context, PREFS) { putBoolean(channel, on) }
    }
}
