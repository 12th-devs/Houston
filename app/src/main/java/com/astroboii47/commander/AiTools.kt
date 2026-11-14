package com.astroboii47.commander

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date

/** Provider-neutral tool calls. Only an explicit, valid JSON call can run a tool. */
object AiTools {
    data class Result(val text: String, val imagePath: String? = null)
    private val names = setOf("date", "web_search", "weather", "search_files", "search_screenshots",
        "bluetooth_status", "bluetooth_devices", "bluetooth_set", "volume_get", "volume_set",
        "brightness_get", "brightness_set", "music_status", "music_control", "screenshot")

    val instructions = """
        You can use Houston tools. Reply ONLY with a JSON object for one call, or a JSON array for several calls:
        [{"tool":"name","args":{"query":"text","percent":50,"action":"next","enabled":true}}]
        Available tools: date; web_search(query); weather(query, optional city); search_files(query);
        search_screenshots(query); bluetooth_status; bluetooth_devices; bluetooth_set(enabled);
        volume_get; volume_set(percent); brightness_get; brightness_set(percent);
        music_status; music_control(action: play, pause, next, previous); screenshot.
        Use a tool for current facts or device changes. After receiving its result, answer normally.
        Do not invent tool results. You may request another tool if needed.
    """.trimIndent()

    fun parseCalls(answer: String): List<JSONObject> {
        val clean = answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val calls = runCatching {
            if (clean.startsWith("[")) {
                val array = JSONArray(clean)
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            } else listOf(JSONObject(clean))
        }.getOrDefault(emptyList())
        return calls.take(5).filter { it.optString("tool") in names }
    }

    fun label(call: JSONObject): String = call.optString("tool").replace('_', ' ').replaceFirstChar(Char::uppercase)

    suspend fun weather(query: String): String = withContext(Dispatchers.IO) {
        runCatching { fetch("https://wttr.in/${encode(query.take(120))}?format=3").take(500) }
            .getOrElse { "Weather unavailable: ${it.message ?: "network error"}" }
    }

    suspend fun execute(context: Context, call: JSONObject): Result = withContext(Dispatchers.IO) {
        val args = call.optJSONObject("args") ?: JSONObject()
        val query = args.optString("query").trim().take(120)
        runCatching {
            val response = when (call.getString("tool")) {
                "date" -> DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date())
                "web_search" -> {
                    require(query.isNotBlank()) { "Search query is empty" }
                    val xml = fetch("https://www.bing.com/search?format=rss&q=${encode(query)}")
                    Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).take(5).map { match ->
                        val item = match.groupValues[1]
                        listOf("title", "link", "description").joinToString(" · ") { tag ->
                            Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)
                                ?.replace(Regex("<[^>]*>"), "")?.replace("&amp;", "&")?.trim().orEmpty()
                        }
                    }.joinToString("\n").ifBlank { "No web results found" }
                }
                "weather" -> weather(query)
                "search_files" -> {
                    require(query.isNotBlank()) { "File query is empty" }
                    CommandEngine(context as android.app.Activity).searchFiles(query, 10).joinToString("\n") { "${it.name} · ${it.location}" }
                        .ifBlank { "No matching files found or file access is unavailable" }
                }
                "search_screenshots" -> {
                    if (!CommandBarDeviceActions.imagePermissionGranted(context)) "Photo access is needed in Houston settings"
                    else CommandBarDeviceActions.screenshots(context).filter { query.isBlank() || it.name.contains(query, true) }
                        .take(10).joinToString("\n") { it.name }.ifBlank { "No matching screenshots" }
                }
                "bluetooth_status" -> "Bluetooth ${if (DeviceControls.bluetoothEnabled(context)) "on" else "off"}"
                "bluetooth_devices" -> if (!DeviceControls.bluetoothPermission(context)) "Bluetooth permission is needed in Houston settings"
                    else DeviceControls.bluetoothDevices(context).joinToString("\n") { "${it.name} · ${if (it.connected) "connected" else "paired"}" }.ifBlank { "No paired devices" }
                "bluetooth_set" -> DeviceControls.setBluetooth(args.optBoolean("enabled")) ?: "Bluetooth change requested"
                "volume_get" -> "Media volume ${DeviceControls.level(context, DeviceControls.Level.Volume)}%"
                "volume_set" -> DeviceControls.setLevel(context, DeviceControls.Level.Volume, percent(args)) ?: "Media volume set to ${percent(args)}%"
                "brightness_get" -> "Brightness ${DeviceControls.level(context, DeviceControls.Level.Brightness)}%"
                "brightness_set" -> DeviceControls.setLevel(context, DeviceControls.Level.Brightness, percent(args)) ?: "Brightness set to ${percent(args)}%"
                "music_status" -> CommandBarDeviceActions.player(context)?.let { "${it.title} · ${it.artist} · ${if (it.playing) "playing" else "paused"}" } ?: "No active player"
                "music_control" -> {
                    val action = args.optString("action")
                    require(action in setOf("play", "pause", "next", "previous")) { "Unknown music action" }
                    val playing = CommandBarDeviceActions.player(context)?.playing
                    if ((action == "play" && playing != true) || (action == "pause" && playing == true))
                        CommandBarDeviceActions.mediaAction(context, "toggle")
                    else if (action == "next" || action == "previous") CommandBarDeviceActions.mediaAction(context, action)
                    "Music action: $action"
                }
                "screenshot" -> {
                    val activity = context as android.app.Activity
                    val previous = CommandBarDeviceActions.screenshots(context).firstOrNull()?.uri
                    withContext(Dispatchers.Main) { activity.window.decorView.alpha = 0f }
                    try {
                        delay(300)
                        val error = DeviceControls.captureScreenshot(context)
                        if (error != null) Result(error) else {
                            var image: ScreenshotEntry? = null
                            repeat(8) {
                                delay(400)
                                if (image == null) image = CommandBarDeviceActions.screenshots(context).firstOrNull { it.uri != previous }
                            }
                            if (image == null) Result("Screenshot saved, but photo access is needed to attach it to the chat")
                            else {
                                val path = context.filesDir.resolve("ai_screenshot_${System.currentTimeMillis()}.jpg")
                                val uri = image.uri
                                val copied = runCatching {
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        path.outputStream().use(input::copyTo)
                                    } != null
                                }.getOrDefault(false)
                                if (copied) Result("Screenshot captured and attached", path.absolutePath)
                                else Result("Screenshot saved, but it could not be attached")
                            }
                        }
                    } finally {
                        withContext(Dispatchers.Main) { activity.window.decorView.alpha = 1f }
                    }
                }
                else -> "Unknown tool"
            }
            if (response is Result) response else Result(response.toString())
        }.getOrElse { Result("Tool error: ${it.message ?: "unavailable"}") }
    }

    private fun percent(args: JSONObject): Int {
        require(args.has("percent")) { "Missing percent" }
        return args.getInt("percent").coerceIn(0, 100)
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun fetch(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "Houston/1.0")
            require(connection.responseCode in 200..299) { "Service returned ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText().take(40_000) }
        } finally { connection.disconnect() }
    }
}
