package com.astroboii47.commander

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bounded Meet Mobile import built on the [MeetMobile] adapter.
 * Only allowlisted public competition endpoints; any 401/402/403/429 or
 * schema mismatch surfaces as an honest unavailable state. Swimmer identity
 * must be confirmed (roster name match + explicit user confirm) before any
 * result is imported. Raw values preserved; no cross-course comparison.
 */

data class MmMeet(val id: String, val name: String, val city: String = "")
data class MmEventNode(val id: String, val name: String)
data class MmEntry(
    val eventName: String,
    val timeSec: Double?,
    val place: Int?,
    val splits: List<RaceSplit>,
    val dq: Boolean,
    val swimmerLabel: String,
)

private fun JVal.objText(vararg keys: String): String? {
    if (this !is JVal.Obj) return null
    for (k in keys) {
        val v = m[k] as? JVal.Str ?: continue
        if (v.s.isNotBlank()) return v.s
    }
    return null
}

private fun JVal.objNum(vararg keys: String): Double? {
    if (this !is JVal.Obj) return null
    for (k in keys) {
        val v = m[k] as? JVal.Num ?: continue
        return v.v
    }
    return null
}

/** Time cells look like 68.2, "1:08.20", or {time/cumulativeTime}. */
fun mmTimeToSec(v: JVal?): Double? = when (v) {
    is JVal.Num -> v.v.takeIf { it in 0.0..5999.0 }
    is JVal.Str -> parseSwimTime(v.s)
    is JVal.Obj -> mmTimeToSec(v.m["time"] ?: v.m["cumulativeTime"] ?: v.m["finalTime"])
    else -> null
}

fun mmIsDq(e: JVal.Obj): Boolean {
    val flags = listOf("disqualified", "dq", "isDq", "scratched", "noShow")
    for (f in flags) {
        val v = e.m[f] ?: continue
        if (v is JVal.Bool && v.v) return true
        if (v is JVal.Num && v.v != 0.0) return true
        if (v is JVal.Str && v.s.equals("true", true)) return true
    }
    val reason = (e.m["dqReason"] as? JVal.Str)?.s ?: (e.m["disqualificationReason"] as? JVal.Str)?.s
    return !reason.isNullOrBlank()
}

/** Search payload → meets (tolerant: list or {meets:[...]}). */
fun parseMmMeets(payload: JVal): List<MmMeet> {
    val arr: List<JVal> = when (payload) {
        is JVal.Arr -> payload.l
        is JVal.Obj -> {
            (payload.m["meets"] as? JVal.Arr)?.l
                ?: (payload.m["results"] as? JVal.Arr)?.l
                ?: (payload.m["result"] as? JVal.Arr)?.l
                ?: return emptyList()
        }
        else -> return emptyList()
    }
    return arr.mapNotNull { e ->
        if (e !is JVal.Obj) return@mapNotNull null
        val id = e.objNum("meetId", "id")?.toLong()?.toString()
            ?: e.objText("meetId", "id") ?: return@mapNotNull null
        val name = e.objText("meetName", "name") ?: "Meet $id"
        MmMeet(id, name, e.objText("city", "cityName").orEmpty())
    }.take(50)
}

/** Meet/program payload → event nodes (tolerant drill-down). */
fun parseMmEvents(payload: JVal): List<MmEventNode> {
    val out = LinkedHashMap<String, String>()
    fun walk(v: JVal, depth: Int) {
        if (depth > 6) return
        when (v) {
            is JVal.Arr -> v.l.forEach { walk(it, depth + 1) }
            is JVal.Obj -> {
                val id = v.objNum("eventId", "roundId", "id")?.toLong()?.toString()
                    ?: v.objText("eventId", "roundId", "id")
                val name = v.objText("eventName", "name", "roundName", "title")
                if (id != null && name != null && ("event" in v.m || "round" in v.m || depth >= 2)) {
                    out.putIfAbsent(id, name)
                }
                v.m.values.forEach { walk(it, depth + 1) }
            }
            else -> Unit
        }
    }
    walk(payload, 0)
    return out.entries.take(200).map { MmEventNode(it.key, it.value) }
}

/** Round payload → entries with finish times/places/splits where published. */
fun parseMmRound(payload: JVal, eventName: String): List<MmEntry> {
    val holders = mutableListOf<JVal.Obj>()
    fun walk(v: JVal, depth: Int) {
        if (depth > 7) return
        when (v) {
            is JVal.Arr -> v.l.forEach { walk(it, depth + 1) }
            is JVal.Obj -> {
                val hasTime = v.objNum("finishTime", "finalTime", "time", "resultTime") != null ||
                    mmTimeToSec(v.m["finishTime"] ?: v.m["finalTime"] ?: v.m["time"]) != null
                val hasPlace = v.objNum("place", "overallPlace", "finishPlace") != null
                val hasSwimmer = v.objText("firstName", "name", "swimmerName") != null || v.m["swimmer"] is JVal.Obj
                if ((hasTime || hasPlace) && hasSwimmer) holders.add(v)
                else v.m.values.forEach { walk(it, depth + 1) }
            }
            else -> Unit
        }
    }
    walk(payload, 0)
    return holders.take(500).map { e ->
        val first = e.objText("firstName") ?: (e.m["swimmer"] as? JVal.Obj)?.objText("firstName").orEmpty()
        val last = e.objText("lastName") ?: (e.m["swimmer"] as? JVal.Obj)?.objText("lastName").orEmpty()
        val label = "$first $last".trim().ifEmpty { e.objText("name", "swimmerName") ?: "Unknown swimmer" }
        val splits = ((e.m["splits"] as? JVal.Arr)?.l ?: emptyList()).mapNotNull { s ->
            if (s !is JVal.Obj) return@mapNotNull null
            val t = mmTimeToSec(s.m["time"]) ?: return@mapNotNull null
            val cum = mmTimeToSec(s.m["cumulativeTime"]) ?: t
            val dist = (s.objNum("distance", "distanceM", "sequence") ?: 0.0).toInt()
            RaceSplit(dist, t, cum)
        }
        MmEntry(
            eventName = eventName,
            timeSec = mmTimeToSec(e.m["finishTime"] ?: e.m["finalTime"] ?: e.m["time"] ?: e.m["resultTime"]),
            place = e.objNum("overallPlace", "place", "finishPlace")?.toInt(),
            splits = splits,
            dq = mmIsDq(e),
            swimmerLabel = label,
        )
    }
}

private fun mmHeaders(authKey: String): Map<String, String> = mapOf(
    "Content-Type" to "application/json",
    "Accept" to "application/json",
    "User-Agent" to "HoustonSwim/3.0",
    "auth_key" to authKey,
)

suspend fun mmGet(endpoint: String, params: Map<String, Any?>): JVal = withContext(Dispatchers.IO) {
    val route = MeetMobile.ALLOWLIST[endpoint] ?: throw IllegalArgumentException("Endpoint is not in the read-only allowlist.")
    val (_, path) = route
    val qs = params.filterValues { it != null }.entries.joinToString("&") {
        "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value.toString(), "UTF-8")}"
    }
    val conn = (URL(MeetMobile.BASE_URL + path + "?$qs").openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = false
        connectTimeout = 25000
        readTimeout = 25000
        requestMethod = "GET"
        for ((k, v) in mmHeaders(MeetMobile.parameterDigest(params))) setRequestProperty(k, v)
    }
    try {
        val status = conn.responseCode
        when (status) {
            401, 402, 403 -> throw MeetMobile.MeetMobileException.Restricted()
            429 -> throw MeetMobile.MeetMobileException.RateLimited(120)
            200 -> Unit
            else -> throw MeetMobile.MeetMobileException.Http(status)
        }
        val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val payload = parseJson(body)
        if (payload !is JVal.Obj) throw MeetMobile.MeetMobileException.Schema("Response envelope changed.")
        val code = payload.obj("code")?.num()?.toInt()
        if (code != 1) throw MeetMobile.MeetMobileException.Api("Meet Mobile reported an unsuccessful request.")
        payload.obj("result") ?: payload.obj("results")
        ?: throw MeetMobile.MeetMobileException.Schema("Successful response is missing result/results.")
    } finally {
        conn.disconnect()
    }
}

fun mmUserMessage(e: Exception): String = when (e) {
    is MeetMobile.MeetMobileException.Restricted -> "Meet Mobile needs authorization for this request — showing what's available without it."
    is MeetMobile.MeetMobileException.RateLimited -> "Meet Mobile rate limit reached. Automatic requests are paused; manual results still work."
    is MeetMobile.MeetMobileException.Http -> "Meet Mobile returned HTTP ${e.status}. No alternate route was tried."
    is MeetMobile.MeetMobileException.Schema -> "Meet Mobile changed its response shape (${e.message}). Nothing was imported."
    is MeetMobile.MeetMobileException.Api -> e.message ?: "Meet Mobile reported an unsuccessful request."
    else -> "Meet Mobile request failed: ${(e.message ?: "network error").take(140)}"
}
