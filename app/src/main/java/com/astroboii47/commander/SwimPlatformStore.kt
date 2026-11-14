package com.astroboii47.commander

import android.content.Context
import java.io.File

/**
 * File-based swim platform persistence. Migrates the legacy single-goal
 * `swim_log` prefs + `swim/swim.json` forward without losing data:
 * the old event becomes goal #1, old log entries become practice sessions
 * with title/detail preserved.
 */
object SwimPlatformStore {
    private const val PREFS = "swim_platform"
    private const val KEY_PROFILE = "profile_json"
    private const val KEY_GOALS = "goals_json"
    private const val KEY_RESULTS_SEEN = "results_seen"

    private fun dir(context: Context): File = File(context.filesDir, "swim").apply { mkdirs() }
    private fun practicesFile(context: Context): File = File(dir(context), "practices.json")
    private fun resultsFile(context: Context): File = File(dir(context), "results.json")

    // ----- profile -----

    fun loadProfile(context: Context): SwimmerProfile {
        val raw = SettingsStore.getString(context, PREFS, KEY_PROFILE) ?: return SwimmerProfile()
        if (raw.isBlank()) return SwimmerProfile()
        return runCatching { profileFromJson(parseJson(raw)) }.getOrDefault(SwimmerProfile())
    }

    fun saveProfile(context: Context, p: SwimmerProfile) {
        SettingsStore.edit(context, PREFS) {
            putString(
                KEY_PROFILE,
                "{\"name\":\"${escapeJson(p.name)}\",\"club\":\"${escapeJson(p.club)}\"," +
                    "\"school\":\"${escapeJson(p.schoolTeam)}\",\"course\":\"${p.preferredCourse}\"," +
                    "\"mmid\":\"${escapeJson(p.meetMobileSwimmerId)}\"," +
                    "\"strokes\":[${p.strokes.joinToString(",") { "\"${escapeJson(it)}\"" }}]," +
                    "\"links\":[${p.links.take(10).joinToString(",") {
                        "{\"u\":\"${escapeJson(it.uniqueId)}\",\"l\":\"${escapeJson(it.label)}\",\"m\":\"${escapeJson(it.meetName)}\"}"
                    }}]}",
            )
        }
    }

    private fun profileFromJson(v: JVal): SwimmerProfile {
        if (v !is JVal.Obj) return SwimmerProfile()
        return SwimmerProfile(
            name = v.objStr("name").orEmpty(),
            club = v.objStr("club").orEmpty(),
            schoolTeam = v.objStr("school").orEmpty(),
            preferredCourse = parseSwimCourse(v.objStr("course")),
            meetMobileSwimmerId = v.objStr("mmid").orEmpty(),
            strokes = v.objArr("strokes")?.mapNotNull { (it as? JVal.Str)?.s } ?: emptyList(),
            links = v.objArr("links")?.mapNotNull { e ->
                if (e !is JVal.Obj) return@mapNotNull null
                LinkedSwimmer(
                    uniqueId = e.objStr("u") ?: return@mapNotNull null,
                    label = e.objStr("l") ?: e.objStr("u") ?: "",
                    meetName = e.objStr("m").orEmpty(),
                )
            } ?: emptyList(),
        )
    }

    // ----- goals -----

    fun loadGoals(context: Context): List<SwimGoal> {
        migrateLegacyIfNeeded(context)
        val raw = SettingsStore.getString(context, PREFS, KEY_GOALS) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching { goalsFromJson(parseJson(raw)) }.getOrDefault(emptyList())
    }

    fun saveGoals(context: Context, goals: List<SwimGoal>) {
        SettingsStore.edit(context, PREFS) { putString(KEY_GOALS, goalsToJson(goals.take(50))) }
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        val p = SettingsStore.prefs(context, PREFS)
        if (p.contains(KEY_GOALS)) return
        // Legacy lives in "swim_log" prefs + swim.json entries; migrate once.
        val legacy = runCatching { SwimStore.load(context) }.getOrNull()
        if (legacy == null) {
            SettingsStore.edit(context, PREFS) { putString(KEY_GOALS, "[]") }
            return
        }
        val goals = if (legacy.eventName.isNotBlank() &&
            (legacy.baselineSec != null || legacy.bestSec != null || legacy.goalSec != null)
        ) {
            listOf(
                SwimGoal(
                    id = newSwimGoalId(),
                    event = legacy.eventName.replace(Regex("\\s*[–-]\\s*SC[YM]|\\s*[–-]\\s*LCM", RegexOption.IGNORE_CASE), "").trim().ifEmpty { "100 Breast" },
                    course = when {
                        legacy.eventName.contains("LCM", true) -> SwimCourse.LCM
                        legacy.eventName.contains("SCM", true) -> SwimCourse.SCM
                        else -> SwimCourse.SCY
                    },
                    targetSec = legacy.goalSec ?: legacy.bestSec ?: legacy.baselineSec ?: 65.0,
                    startSec = legacy.baselineSec,
                    currentBestSec = legacy.bestSec,
                ),
            )
        } else emptyList()
        saveGoals(context, goals)
        if (legacy.log.isNotEmpty()) {
            val sessions = legacy.log.map {
                PracticeSession(id = it.id, dateIso = it.date, notes = listOfNotNull(it.title, it.detail).joinToString(" — "))
            }
            savePractices(context, sessions)
        }
    }

    internal fun goalsToJson(goals: List<SwimGoal>): String = buildString {
        append('[')
        goals.forEachIndexed { i, g ->
            if (i > 0) append(',')
            append("{\"id\":\"${escapeJson(g.id)}\",\"event\":\"${escapeJson(g.event)}\",\"course\":\"${g.course}\",")
            append("\"target\":${g.targetSec},\"targetDate\":${g.targetDateIso?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"start\":${g.startSec?.toString() ?: "null"},\"best\":${g.currentBestSec?.toString() ?: "null"},")
            append("\"completed\":${g.completed},\"ref\":${g.resultRef?.let { "\"${escapeJson(it)}\"" } ?: "null"},\"hist\":[")
            g.history.forEachIndexed { j, h ->
                if (j > 0) append(',')
                append("{\"d\":\"${escapeJson(h.dateIso)}\",\"t\":${h.bestSec},\"n\":\"${escapeJson(h.note)}\"}")
            }
            append("]}")
        }
        append(']')
    }

    internal fun goalsFromJson(v: JVal): List<SwimGoal> {
        val arr = v.arr() ?: return emptyList()
        return arr.mapNotNull { e ->
            if (e !is JVal.Obj) return@mapNotNull null
            SwimGoal(
                id = e.objStr("id") ?: return@mapNotNull null,
                event = e.objStr("event") ?: "Swim",
                course = parseSwimCourse(e.objStr("course")),
                targetSec = e.obj("target")?.num() ?: return@mapNotNull null,
                targetDateIso = e.objStr("targetDate"),
                startSec = e.obj("start")?.num(),
                currentBestSec = e.obj("best")?.num(),
                history = e.objArr("hist")?.mapNotNull { h ->
                    if (h !is JVal.Obj) return@mapNotNull null
                    SwimGoalHistory(h.objStr("d") ?: return@mapNotNull null, h.obj("t")?.num() ?: return@mapNotNull null, h.objStr("n").orEmpty())
                } ?: emptyList(),
                completed = e.objBool("completed") == true,
                resultRef = e.objStr("ref"),
            )
        }
    }

    // ----- practices -----

    fun loadPractices(context: Context): List<PracticeSession> {
        val f = practicesFile(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            (parseJson(f.readText()) as? JVal.Arr)?.l?.mapNotNull { practicesFromJson(it) } ?: emptyList()
        }.getOrDefault(emptyList()).sortedByDescending { it.dateIso }
    }

    fun savePractices(context: Context, sessions: List<PracticeSession>) {
        val b = StringBuilder("[")
        sessions.take(300).forEachIndexed { i, s ->
            if (i > 0) b.append(',')
            b.append("{\"id\":\"${escapeJson(s.id)}\",\"d\":\"${escapeJson(s.dateIso)}\",\"c\":\"${s.poolCourse}\",")
            b.append("\"dist\":${s.distanceYd?.toString() ?: "null"},\"dur\":${s.durationMin?.toString() ?: "null"},")
            b.append("\"strokes\":[${s.strokes.joinToString(",") { "\"${escapeJson(it)}\"" }}],")
            b.append("\"sets\":\"${escapeJson(s.sets)}\",\"int\":\"${escapeJson(s.intervals)}\",")
            b.append("\"rpe\":${s.rpe?.toString() ?: "null"},\"tech\":\"${escapeJson(s.technique)}\",")
            b.append("\"coach\":\"${escapeJson(s.coachFeedback)}\",\"notes\":\"${escapeJson(s.notes)}\",")
            b.append("\"voice\":\"${escapeJson(s.voiceRef)}\",")
            b.append("\"goals\":[${s.goalIds.joinToString(",") { "\"${escapeJson(it)}\"" }}],")
            b.append("\"mems\":[${s.memoryIds.joinToString(",") { "\"${escapeJson(it)}\"" }}]}")
        }
        b.append(']')
        val tmp = File(dir(context), "practices.json.tmp")
        tmp.writeText(b.toString())
        val dst = practicesFile(context)
        if (!tmp.renameTo(dst)) {
            dst.delete()
            tmp.renameTo(dst)
        }
    }

    private fun practicesFromJson(e: JVal): PracticeSession? {
        if (e !is JVal.Obj) return null
        return PracticeSession(
            id = e.objStr("id") ?: return null,
            dateIso = e.objStr("d") ?: return null,
            poolCourse = parseSwimCourse(e.objStr("c")),
            distanceYd = e.obj("dist")?.num()?.toInt(),
            durationMin = e.obj("dur")?.num()?.toInt(),
            strokes = e.objArr("strokes")?.mapNotNull { (it as? JVal.Str)?.s } ?: emptyList(),
            sets = e.objStr("sets").orEmpty(),
            intervals = e.objStr("int").orEmpty(),
            rpe = e.obj("rpe")?.num()?.toInt(),
            technique = e.objStr("tech").orEmpty(),
            coachFeedback = e.objStr("coach").orEmpty(),
            notes = e.objStr("notes").orEmpty(),
            voiceRef = e.objStr("voice").orEmpty(),
            goalIds = e.objArr("goals")?.mapNotNull { (it as? JVal.Str)?.s } ?: emptyList(),
            memoryIds = e.objArr("mems")?.mapNotNull { (it as? JVal.Str)?.s } ?: emptyList(),
        )
    }

    // ----- results -----

    fun loadResults(context: Context): List<RaceResult> {
        val f = resultsFile(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            (parseJson(f.readText()) as? JVal.Arr)?.l?.mapNotNull { resultFromJson(it) } ?: emptyList()
        }.getOrDefault(emptyList()).sortedByDescending { it.dateIso ?: "" }
    }

    fun hasResult(context: Context, dedupeId: String): Boolean = loadResults(context).any { it.id == dedupeId }

    /** Idempotent import: returns only genuinely new results. */
    fun importResults(context: Context, results: List<RaceResult>): List<RaceResult> {
        val seen = loadResults(context).associateBy { it.id }.toMutableMap()
        val fresh = results.filter { it.id !in seen && it.timeSec != null }
        if (fresh.isEmpty()) return emptyList()
        fresh.forEach { seen[it.id] = it }
        persistResults(context, seen.values.toList())
        SettingsStore.edit(context, PREFS) { putString(KEY_RESULTS_SEEN, java.time.Instant.now().toString()) }
        return fresh
    }

    fun lastResultsSync(context: Context): String? = SettingsStore.getString(context, PREFS, KEY_RESULTS_SEEN)

    private fun persistResults(context: Context, results: List<RaceResult>) {
        val b = StringBuilder("[")
        results.take(500).forEachIndexed { i, r ->
            if (i > 0) b.append(',')
            b.append("{\"id\":\"${escapeJson(r.id)}\",\"meet\":\"${escapeJson(r.meetId)}\",\"mn\":\"${escapeJson(r.meetName)}\",")
            b.append("\"ev\":\"${escapeJson(r.eventId)}\",\"en\":\"${escapeJson(r.eventName)}\",\"c\":\"${r.course}\",")
            b.append("\"t\":${r.timeSec?.toString() ?: "null"},\"pl\":${r.place?.toString() ?: "null"},")
            b.append("\"d\":${r.dateIso?.let { "\"${escapeJson(it)}\"" } ?: "null"},\"ref\":\"${escapeJson(r.swimmerRef)}\",")
            b.append("\"dq\":${r.dq},\"prov\":\"${escapeJson(r.provenance)}\",\"sp\":[")
            r.splits.forEachIndexed { j, s ->
                if (j > 0) b.append(',')
                b.append("{\"dm\":${s.distanceM},\"t\":${s.timeSec},\"c\":${s.cumulativeSec}}")
            }
            b.append("]}")
        }
        b.append(']')
        val tmp = File(dir(context), "results.json.tmp")
        tmp.writeText(b.toString())
        val dst = resultsFile(context)
        if (!tmp.renameTo(dst)) {
            dst.delete()
            tmp.renameTo(dst)
        }
    }

    private fun resultFromJson(e: JVal): RaceResult? {
        if (e !is JVal.Obj) return null
        return RaceResult(
            id = e.objStr("id") ?: return null,
            meetId = e.objStr("meet") ?: "",
            meetName = e.objStr("mn") ?: "",
            eventId = e.objStr("ev") ?: "",
            eventName = e.objStr("en") ?: "",
            course = parseSwimCourse(e.objStr("c")),
            timeSec = e.obj("t")?.num(),
            place = e.obj("pl")?.num()?.toInt(),
            dateIso = e.objStr("d"),
            swimmerRef = e.objStr("ref").orEmpty(),
            splits = e.objArr("sp")?.mapNotNull { s ->
                if (s !is JVal.Obj) return@mapNotNull null
                RaceSplit(s.obj("dm")?.num()?.toInt() ?: return@mapNotNull null, s.obj("t")?.num() ?: return@mapNotNull null, s.obj("c")?.num() ?: return@mapNotNull null)
            } ?: emptyList(),
            dq = e.objBool("dq") == true,
            provenance = e.objStr("prov") ?: "manual",
        )
    }
}
