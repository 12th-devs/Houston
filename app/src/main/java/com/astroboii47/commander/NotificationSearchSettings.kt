package com.astroboii47.commander

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object NotificationSearchSettings {
    private const val PREFS = "commander_notification_search"
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, 0).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", enabled).apply()
    fun hasAccess(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun snapshotFile(context: Context) = java.io.File(java.io.File(context.filesDir, "notifications").apply { mkdirs() }, "live.json")

    /** Latest snapshot written by [HoustonNotificationService]; newest first. */
    fun live(context: Context): List<LiveNotification> {
        return runCatching {
            val f = snapshotFile(context)
            if (!f.exists()) return emptyList()
            val root = parseJson(f.readText())
            (root as? JVal.Arr)?.l?.mapNotNull { e ->
                if (e !is JVal.Obj) return@mapNotNull null
                LiveNotification(
                    pkg = e.objStr("pkg") ?: return@mapNotNull null,
                    title = e.objStr("title") ?: "",
                    text = e.objStr("text") ?: "",
                    postedAt = (e.obj("postedAt") as? JVal.Num)?.v?.toLong() ?: 0L,
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    internal fun saveSnapshot(context: Context, items: List<LiveNotification>) {
        runCatching {
            val b = StringBuilder("[")
            items.forEachIndexed { i, n ->
                if (i > 0) b.append(',')
                b.append("{\"pkg\":\"${escapeJson(n.pkg)}\",\"title\":\"${escapeJson(n.title)}\",")
                b.append("\"text\":\"${escapeJson(n.text)}\",\"postedAt\":${n.postedAt}}")
            }
            b.append(']')
            val tmp = java.io.File(snapshotFile(context).parent, "live.json.tmp")
            tmp.writeText(b.toString())
            val dst = snapshotFile(context)
            if (!tmp.renameTo(dst)) {
                dst.delete()
                tmp.renameTo(dst)
            }
        }
    }
}
