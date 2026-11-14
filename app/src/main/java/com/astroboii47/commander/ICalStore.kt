package com.astroboii47.commander

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** iCal (ICS-over-HTTPS) subscriptions for the dashboard calendar.
 *  Feeds live in SharedPreferences; raw .ics bodies in internal storage.
 *  Supports plain https:// calendar links (Google "Secret address in iCal
 *  format", Outlook ICS, school district feeds, ...). */

data class ICalFeed(
    val id: String,
    val name: String,
    val url: String,
    val lastSync: String?,
    val lastError: String?,
)

object ICalStore {
    private const val PREFS = "ical_feeds"
    private const val KEY_IDS = "feed_ids"

    private fun dir(context: Context): File = File(context.filesDir, "ical").apply { mkdirs() }
    private fun icsFile(context: Context, id: String): File = File(dir(context), "$id.ics")

    fun list(context: Context): List<ICalFeed> {
        val p = SettingsStore.prefs(context, PREFS)
        val ids = p.getString(KEY_IDS, "").orEmpty().split('|').filter { it.isNotBlank() }
        return ids.mapNotNull { id ->
            val name = p.getString("feed_${id}_name", null) ?: return@mapNotNull null
            val url = p.getString("feed_${id}_url", null) ?: return@mapNotNull null
            ICalFeed(
                id = id,
                name = name,
                url = url,
                lastSync = p.getString("feed_${id}_sync", null),
                lastError = p.getString("feed_${id}_error", null),
            )
        }
    }

    fun add(context: Context, name: String, url: String): ICalFeed {
        val id = "f${System.currentTimeMillis().toString(36)}${(0..999).random().toString(36)}"
        val clean = url.trim()
        require(clean.startsWith("http://") || clean.startsWith("https://")) { "Feed URL must start with http(s)." }
        val ids = SettingsStore.getString(context, PREFS, KEY_IDS).orEmpty()
        SettingsStore.edit(context, PREFS) {
            putString(KEY_IDS, (ids.split('|').filter { it.isNotBlank() } + id).joinToString("|"))
            putString("feed_${id}_name", name.trim().ifEmpty { clean })
            putString("feed_${id}_url", clean)
            remove("feed_${id}_error")
        }
        return list(context).first { it.id == id }
    }

    fun remove(context: Context, id: String) {
        val ids = SettingsStore.getString(context, PREFS, KEY_IDS).orEmpty()
        SettingsStore.edit(context, PREFS) {
            putString(KEY_IDS, ids.split('|').filter { it.isNotBlank() && it != id }.joinToString("|"))
            remove("feed_${id}_name"); remove("feed_${id}_url")
            remove("feed_${id}_sync"); remove("feed_${id}_error")
        }
        icsFile(context, id).delete()
    }

    fun rawIcs(context: Context, id: String): String? =
        icsFile(context, id).takeIf { it.exists() }?.readText()

    suspend fun refresh(context: Context, feed: ICalFeed): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(feed.url)
            require(url.protocol == "http" || url.protocol == "https") { "Only http(s) feeds are supported." }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Houston/1.0 (Android; +ical)")
                setRequestProperty("Accept", "text/calendar, text/plain, */*")
            }
            val status = conn.responseCode
            if (status !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Feed returned HTTP $status.")
            }
            val body = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            conn.disconnect()
            if (body.length > 8_000_000) throw IllegalStateException("Feed is too large (>8 MB).")
            val events = parseICalendar(body)
            icsFile(context, feed.id).writeText(body)
            SettingsStore.edit(context, PREFS) {
                putString("feed_${feed.id}_sync", java.time.Instant.now().toString())
                remove("feed_${feed.id}_error")
            }
            events.size
        }.onFailure { e ->
            SettingsStore.edit(context, PREFS) {
                putString("feed_${feed.id}_error", (e.message ?: "Sync failed").take(220))
            }
        }
    }

    /** All occurrences from every feed inside [from]..[to], tolerant of per-feed failures. */
    fun occurrences(
        context: Context,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ICalOccurrence> {
        val out = mutableListOf<ICalOccurrence>()
        for (feed in list(context)) {
            val raw = rawIcs(context, feed.id) ?: continue
            val events = runCatching { parseICalendar(raw) }.getOrDefault(emptyList())
            out.addAll(runCatching { expandICalEvents(events, feed.id, feed.name, from, to) }.getOrDefault(emptyList()))
        }
        return out.sortedWith(compareBy({ it.date }, { it.allDay }, { it.startTime }, { it.summary }))
    }
}
