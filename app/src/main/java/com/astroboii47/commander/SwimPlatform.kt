package com.astroboii47.commander

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/**
 * Houston swimming platform: profile, multi-goal tracking, rich practice
 * journal, race results (manual + Meet Mobile adapter), analytics.
 *
 * Course integrity: SCY/SCM/LCM are NEVER compared across courses. Every
 * goal, best, and result is keyed by (event, course).
 */

// ---------- Courses & events ----------

enum class SwimCourse { SCY, SCM, LCM }

fun swimCourseLabel(c: SwimCourse): String = when (c) {
    SwimCourse.SCY -> "SCY"
    SwimCourse.SCM -> "SCM"
    SwimCourse.LCM -> "LCM"
}

fun parseSwimCourse(s: String?): SwimCourse = when (s?.trim()?.uppercase()) {
    "SCM" -> SwimCourse.SCM
    "LCM" -> SwimCourse.LCM
    else -> SwimCourse.SCY
}

data class SwimmerProfile(
    val name: String = "",
    val club: String = "",
    val schoolTeam: String = "",
    val preferredCourse: SwimCourse = SwimCourse.SCY,
    val meetMobileSwimmerId: String = "", // verified unique-swimmer id only
    val strokes: List<String> = emptyList(),
    val links: List<LinkedSwimmer> = emptyList(), // extra verified identities
)

/** One verified Meet Mobile identity attached to the same swimmer. */
data class LinkedSwimmer(
    val uniqueId: String,
    val label: String, // e.g. "Jane Doe · Nitro Swim"
    val meetName: String = "",
)

/**
 * Identity matching for imports: a result belongs to this swimmer when its
 * entry label matches the profile name or any linked identity. Explicit
 * confirmation in the UI is still required before importing.
 */
fun entryMatchesIdentity(label: String, profileName: String, links: List<LinkedSwimmer>): Boolean {
    val t = label.trim()
    if (t.isEmpty()) return false
    val cands = (listOf(profileName) + links.map { it.label } + links.map { it.uniqueId })
        .map { it.trim() }.filter { it.length >= 2 }
    return cands.any { c ->
        t.contains(c, ignoreCase = true) || c.contains(t, ignoreCase = true)
    }
}

// ---------- Goals ----------

data class SwimGoalHistory(val dateIso: String, val bestSec: Double, val note: String = "")

data class SwimGoal(
    val id: String,
    val event: String, // e.g. "100 Breast"
    val course: SwimCourse,
    val targetSec: Double,
    val targetDateIso: String? = null,
    val startSec: Double? = null,
    val currentBestSec: Double? = null,
    val history: List<SwimGoalHistory> = emptyList(),
    val completed: Boolean = false,
    val resultRef: String? = null, // winning RaceResult id
) {
    val label: String get() = "$event — ${swimCourseLabel(course)}"
}

/** Progress 0..1 from start through best toward goal. Null when undefined. */
fun swimGoalProgress(g: SwimGoal): Double? {
    val start = g.startSec ?: return null
    val best = g.currentBestSec ?: return null
    val goal = g.targetSec
    if (start <= goal || best < 0) return null
    return ((start - best) / (start - goal)).coerceIn(0.0, 1.0)
}

fun newSwimGoalId(): String =
    "g${System.currentTimeMillis().toString(36)}${(0..999).random().toString(36)}"

// ---------- Practice journal ----------

data class PracticeSession(
    val id: String,
    val dateIso: String, // YYYY-MM-DD
    val poolCourse: SwimCourse = SwimCourse.SCY,
    val distanceYd: Int? = null,
    val durationMin: Int? = null,
    val strokes: List<String> = emptyList(),
    val sets: String = "",
    val intervals: String = "",
    val rpe: Int? = null, // 1..10
    val technique: String = "",
    val coachFeedback: String = "",
    val notes: String = "",
    val voiceRef: String = "", // app-private voice note (see MemoryMedia)
    val goalIds: List<String> = emptyList(),
    val memoryIds: List<String> = emptyList(),
)

fun newPracticeId(): String =
    "p${System.currentTimeMillis().toString(36)}${(0..999).random().toString(36)}"

val PRACTICE_TEMPLATES: List<Pair<String, String>> = listOf(
    "Easy aerobic" to "Warm-up 400 · Kick 4x50 · Main 10x100 @1:30 · Cool 200",
    "Threshold" to "Warm-up 500 · Drill 6x50 · Main 5x200 descend · Cool 300",
    "Sprint" to "Warm-up 400 · Build 8x25 · Main 12x50 @:60 fast · Cool 200",
    "Technique" to "Warm-up 300 · Drill 8x50 (catch/pull) · Main 6x100 smooth · Cool 200",
    "Distance" to "Warm-up 600 · Main 3x800 steady · Cool 200",
)

// ---------- Race results ----------

data class RaceSplit(val distanceM: Int, val timeSec: Double, val cumulativeSec: Double)

data class RaceResult(
    val id: String, // stable dedupe key (see resultDedupeKey)
    val meetId: String,
    val meetName: String,
    val eventId: String,
    val eventName: String,
    val course: SwimCourse,
    val timeSec: Double?,
    val place: Int?,
    val dateIso: String?,
    val swimmerRef: String = "",
    val splits: List<RaceSplit> = emptyList(),
    val dq: Boolean = false,
    val provenance: String = "manual", // "manual" | "meetmobile:<meetId>"
)

/** Deterministic duplicate detection across imports. */
fun resultDedupeKey(
    meetId: String,
    eventId: String,
    course: SwimCourse,
    timeSec: Double?,
    swimmerRef: String,
): String {
    val raw = "$meetId|$eventId|${course}|${timeSec?.toString() ?: "notime"}|$swimmerRef"
    val md = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return md.joinToString("") { "%02x".format(it) }.take(24)
}

/**
 * Matches a result to a goal: same normalized event tokens and same course.
 * Never matches across courses.
 */
fun resultMatchesGoal(result: RaceResult, goal: SwimGoal): Boolean {
    if (result.course != goal.course) return false
    if (result.timeSec == null || result.dq) return false
    fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(" ").filter { it.isNotBlank() }.toSet()
    val r = norm(result.eventName)
    val g = norm(goal.event)
    if (r.isEmpty() || g.isEmpty()) return false
    return g.all { it in r } || r.all { it in g }
}

/** Applies a verified result to matching goals. Returns updated goals + insights. */
fun applyResultToGoals(
    goals: List<SwimGoal>,
    result: RaceResult,
    nowIso: String = Instant.now().toString(),
): Pair<List<SwimGoal>, List<Insight>> {
    val updated = goals.toMutableList()
    val insights = mutableListOf<Insight>()
    for (i in updated.indices) {
        val g = updated[i]
        if (!resultMatchesGoal(result, g)) continue
        val t = result.timeSec ?: continue
        val prevBest = g.currentBestSec
        val isPb = prevBest == null || t < prevBest
        val done = t <= g.targetSec
        val hist = if (isPb) {
            g.history + SwimGoalHistory(result.dateIso ?: LocalDate.now().toString(), t, result.meetName)
        } else g.history
        updated[i] = g.copy(
            currentBestSec = if (isPb) t else g.currentBestSec,
            history = hist.takeLast(50),
            completed = g.completed || done,
            resultRef = if (done) result.id else g.resultRef,
        )
        if (isPb) {
            insights.add(
                Insight(
                    id = "swim.personal_best:${g.id}:$t",
                    kind = InsightKind.SWIM,
                    title = "New best ${g.label}: ${formatSwimTime(t)}",
                    explanation = if (prevBest == null) "First recorded best at ${result.meetName}."
                    else "Previous best ${formatSwimTime(prevBest)} → ${formatSwimTime(t)} " +
                        "(${"%.2f".format(prevBest - t)}s faster) at ${result.meetName}.",
                    atIso = nowIso,
                    refs = listOf("race:${result.id}", "goal:${g.id}"),
                    score = if (done) 100 else 92,
                    actionLabel = "Open goal",
                    actionTarget = "swim:${g.id}",
                ),
            )
        }
        if (done && !g.completed) {
            insights.add(
                Insight(
                    id = "swim.goal_completed:${g.id}",
                    kind = InsightKind.SWIM,
                    title = "Goal complete: ${g.label} ${formatSwimTime(t)}",
                    explanation = "Target was ${formatSwimTime(g.targetSec)}. Verified result ${result.meetName}.",
                    atIso = nowIso,
                    refs = listOf("race:${result.id}", "goal:${g.id}"),
                    score = 100,
                    actionLabel = "Open goal",
                    actionTarget = "swim:${g.id}",
                ),
            )
        }
    }
    return updated to insights
}

// ---------- Meet Mobile adapter ----------

/**
 * Kotlin port of the `Meet Mobile/meetclient/api.py` allowlist + digest.
 * Public competition reads only; any 401/402/403/429 or envelope failure
 * stops with an honest unavailable state — never falls back to legacy
 * routes, alternate identities, receipts, or auth bypass.
 */
object MeetMobile {
    const val BASE_URL = "https://awmobile.active.com/rest/v4/mmo/"
    val ALLOWLIST: Map<String, Pair<String, String>> = mapOf(
        "search" to ("POST" to "meet/search"),
        "meet" to ("GET" to "meet/"),
        "session" to ("GET" to "session/"),
        "event" to ("GET" to "event/"),
        "round" to ("GET" to "round/getProgress"),
        "swimmers" to ("GET" to "swimmer/meetSwimmers"),
        "swimmer" to ("GET" to "swimmer/"),
        "teams" to ("GET" to "team/meetTeams"),
        "team" to ("GET" to "team/"),
        "scores" to ("GET" to "meet/getScores"),
        "standards" to ("GET" to "event/getStandards"),
        "history" to ("GET" to "meet/searchSwimmerMeetsByDate"),
        "swimmer_search" to ("POST" to "swimmer/advancedSearch"),
        "team_history" to ("GET" to "meet/getMeetsForUniqueTeam"),
    )

    sealed class Unavailable(val message: String) {
        class Restricted(msg: String = "Meet Mobile requires authorization or an entitlement for this request.") : Unavailable(msg)
        class RateLimited(val retrySec: Int, msg: String = "Meet Mobile rate limit reached. Automatic requests are paused.") : Unavailable(msg)
        class Schema(msg: String) : Unavailable(msg)
        class Network(msg: String = "Network request failed. Check connectivity and try again.") : Unavailable(msg)
    }

    /** Parameter digest: sorted key:value joined by ';', lowercase SHA-256 hex. */
    fun parameterDigest(params: Map<String, Any?>): String {
        val nonNull = params.filterValues { it != null }
        for ((_, v) in nonNull) {
            require(v !is Map<*, *> && v !is List<*> && v !is Array<*>) {
                "Only flat scalar parameters are supported."
            }
        }
        val canonical = nonNull.toSortedMap().entries.joinToString(";") { (k, v) ->
            "$k:${scalar(v)}"
        }
        if (canonical.isEmpty()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun scalar(v: Any?): String = when (v) {
        is Boolean -> if (v) "true" else "false"
        else -> v.toString()
    }

    /**
     * Live fetch through the existing HTTPS client surface. Kept thin so it
     * stays replaceable; callers must confirm swimmer identity before import
     * and must surface [Unavailable] honestly.
     */
    fun fetch(
        endpoint: String,
        params: Map<String, Any?>,
        postJson: (url: String, json: String, headers: Map<String, String>) -> Pair<Int, String>,
        get: (url: String, headers: Map<String, String>) -> Pair<Int, String>,
    ): Result<JVal> {
        val route = ALLOWLIST[endpoint] ?: return Result.failure(IllegalArgumentException("Endpoint is not in the read-only allowlist."))
        val (method, path) = route
        val clean = params.filterValues { it != null }
        clean.values.forEach { scalar(it) }
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "User-Agent" to "HoustonSwim/3.0",
            "auth_key" to parameterDigest(clean),
        )
        return runCatching {
            val (status, body) = if (method == "GET") {
                val qs = clean.entries.joinToString("&") {
                    "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(scalar(it.value), "UTF-8")}"
                }
                get("$BASE_URL$path?$qs", headers)
            } else {
                val json = "{" + clean.entries.joinToString(",") { (k, v) ->
                    "\"$k\":${if (v is Number || v is Boolean) scalar(v) else "\"$v\""}"
                } + "}"
                postJson(BASE_URL + path, json, headers)
            }
            when (status) {
                401, 402, 403 -> throw MeetMobileException.Restricted()
                429 -> throw MeetMobileException.RateLimited(120)
                200 -> Unit
                else -> throw MeetMobileException.Http(status)
            }
            val payload = parseJson(body)
            if (payload !is JVal.Obj) throw MeetMobileException.Schema("Response envelope changed.")
            val code = payload.obj("code")?.num()?.toInt()
            if (code != 1) {
                val msg = (payload.obj("errorString") as? JVal.Str)?.s?.lowercase().orEmpty()
                val restricted = listOf("auth", "subscription", "purchase", "permission", "denied", "token", "sign in", "login")
                    .any { it in msg }
                if (restricted) throw MeetMobileException.Restricted()
                throw MeetMobileException.Api("Meet Mobile reported an unsuccessful request.")
            }
            payload.obj("result") ?: payload.obj("results") ?: throw MeetMobileException.Schema("Successful response is missing result/results.")
        }
    }

    sealed class MeetMobileException(message: String) : Exception(message) {
        class Restricted(msg: String = "This endpoint requires authorization or an entitlement. Access stopped.") : MeetMobileException(msg)
        class RateLimited(val retrySec: Int) : MeetMobileException("Server rate limit reached. Automatic requests are paused.")
        class Http(val status: Int) : MeetMobileException("Upstream returned HTTP $status. No alternate route was tried.")
        class Api(msg: String) : MeetMobileException(msg)
        class Schema(msg: String) : MeetMobileException(msg)
    }
}

// ---------- Analytics (observed patterns only, no causal claims) ----------

data class ProgressionPoint(val dateIso: String, val timeSec: Double, val label: String)

fun goalProgression(goal: SwimGoal, results: List<RaceResult>): List<ProgressionPoint> {
    return results.filter { resultMatchesGoal(it, goal) && it.timeSec != null }
        .sortedBy { it.dateIso ?: "" }
        .map { ProgressionPoint(it.dateIso ?: "", it.timeSec!!, it.meetName) }
}

fun weeklyYardage(sessions: List<PracticeSession>, weeks: Int = 8): List<Pair<String, Int>> {
    val byWeek = LinkedHashMap<String, Int>()
    for (s in sessions.sortedBy { it.dateIso }) {
        val week = runCatching { LocalDate.parse(s.dateIso).let { "${it.year}-W${it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" } }.getOrNull() ?: continue
        byWeek[week] = (byWeek[week] ?: 0) + (s.distanceYd ?: 0)
    }
    return byWeek.entries.toList().takeLast(weeks).map { it.key to it.value }
}
