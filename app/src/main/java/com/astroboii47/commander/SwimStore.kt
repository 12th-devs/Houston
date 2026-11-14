package com.astroboii47.commander

import android.content.Context
import java.io.File
import java.time.LocalDate

/** Local swim training log: season event, baseline/best/goal times and a
 *  practice log. Everything stays on-device in internal storage. Times are
 *  stored as seconds (e.g. 68.2 = 1:08.20). */

data class SwimState(
    val eventName: String,
    val baselineSec: Double?,
    val bestSec: Double?,
    val goalSec: Double?,
    val log: List<SwimEntry>,
)

data class SwimEntry(
    val id: String,
    val date: String, // YYYY-MM-DD
    val title: String,
    val detail: String?,
)

fun formatSwimTime(totalSec: Double): String {
    val m = (totalSec / 60).toInt()
    val s = totalSec - m * 60
    return "%d:%05.2f".format(m, s)
}

fun parseSwimTime(text: String): Double? {
    val t = text.trim()
    if (t.isEmpty()) return null
    return runCatching {
        if (':' in t) {
            val (m, rest) = t.split(':', limit = 2)
            m.toDouble() * 60 + rest.toDouble()
        } else {
            t.toDouble()
        }
    }.getOrNull()?.takeIf { it in 0.0..5999.0 }
}

/** 0..1 season progress from baseline through best toward goal. Null when unset. */
fun swimProgressPct(start: Double?, best: Double?, goal: Double?): Double? {
    if (start == null || best == null || goal == null) return null
    if (start <= goal || best < 0) return null
    return ((start - best) / (start - goal)).coerceIn(0.0, 1.0)
}

object SwimStore {
    private const val PREFS = "swim_log"

    private fun file(context: Context): File =
        File(File(context.filesDir, "swim").apply { mkdirs() }, "swim.json")

    fun load(context: Context): SwimState {
        val p = SettingsStore.prefs(context, PREFS)
        val f = file(context)
        val entries = if (!f.exists()) emptyList() else runCatching {
            ((parseJson(f.readText()) as? JVal.Arr)?.l ?: emptyList()).mapNotNull { e ->
                if (e !is JVal.Obj) return@mapNotNull null
                SwimEntry(
                    id = e.objStr("id") ?: return@mapNotNull null,
                    date = e.objStr("date") ?: LocalDate.now().toString(),
                    title = e.objStr("title") ?: return@mapNotNull null,
                    detail = e.objStr("detail"),
                )
            }.sortedByDescending { it.date }
        }.getOrDefault(emptyList())
        fun num(key: String): Double? = p.getString(key, null)?.toDoubleOrNull()
        return SwimState(
            eventName = p.getString("event", "100 Breast – SCY") ?: "100 Breast – SCY",
            baselineSec = num("start"),
            bestSec = num("best"),
            goalSec = num("goal"),
            log = entries,
        )
    }

    fun saveTargets(context: Context, eventName: String, start: Double?, best: Double?, goal: Double?) {
        SettingsStore.edit(context, PREFS) {
            putString("event", eventName)
            if (start == null) remove("start") else putString("start", start.toString())
            if (best == null) remove("best") else putString("best", best.toString())
            if (goal == null) remove("goal") else putString("goal", goal.toString())
        }
    }

    fun addEntry(context: Context, date: String, title: String, detail: String?): SwimEntry {
        val entry = SwimEntry(
            id = "s${System.currentTimeMillis().toString(36)}${(0..999).random().toString(36)}",
            date = date, title = title.trim(), detail = detail?.trim()?.ifEmpty { null },
        )
        persist(context, listOf(entry) + load(context).log)
        return entry
    }

    fun removeEntry(context: Context, id: String) {
        persist(context, load(context).log.filter { it.id != id })
    }

    private fun persist(context: Context, entries: List<SwimEntry>) {
        val b = StringBuilder("[")
        entries.take(200).forEachIndexed { i, e ->
            if (i > 0) b.append(',')
            b.append("{\"id\":\"${escapeJson(e.id)}\",\"date\":\"${escapeJson(e.date)}\",")
            b.append("\"title\":\"${escapeJson(e.title)}\",")
            b.append("\"detail\":${e.detail?.let { "\"${escapeJson(it)}\"" } ?: "null"}}")
        }
        b.append(']')
        val tmp = File(file(context).parent, "swim.json.tmp")
        tmp.writeText(b.toString())
        val dst = file(context)
        if (!tmp.renameTo(dst)) {
            dst.delete()
            tmp.renameTo(dst)
        }
    }
}
