package com.astroboii47.commander

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keyless Open-Meteo forecast for the "Your Day" banner. Cached on-device;
 *  refreshed at most hourly. Location is a saved lat/lon pair (no permission).
 *  Default is Louisville, KY. */

data class WxToday(
    val date: String,
    val hiF: Double,
    val loF: Double,
    val code: Int,
    val fetchedAt: String,
)

fun parseOpenMeteo(body: String): WxToday? {
    val root = try {
        parseJson(body)
    } catch (_: Exception) {
        return null
    }
    if (root !is JVal.Obj) return null
    val daily = root.obj("daily") as? JVal.Obj ?: return null
    fun arr(key: String): List<JVal> = daily.objArr(key) ?: emptyList()
    val dates = arr("time").mapNotNull { it.str() }
    val his = arr("temperature_2m_max").mapNotNull { (it as? JVal.Num)?.v }
    val los = arr("temperature_2m_min").mapNotNull { (it as? JVal.Num)?.v }
    val codes = arr("weathercode").mapNotNull { (it as? JVal.Num)?.v?.toInt() }
    if (dates.isEmpty() || his.isEmpty() || los.isEmpty()) return null
    return WxToday(
        date = dates[0],
        hiF = his[0],
        loF = los[0],
        code = codes.firstOrNull() ?: 3,
        fetchedAt = java.time.Instant.now().toString(),
    )
}

/** WMO code -> (label, night-agnostic icon kind). */
fun wxKind(code: Int): String = when (code) {
    0 -> "sunny"
    1, 2 -> "partly"
    3 -> "cloudy"
    45, 48 -> "fog"
    51, 53, 55, 56, 57 -> "drizzle"
    61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
    71, 73, 75, 77, 85, 86 -> "snow"
    95, 96, 99 -> "storm"
    else -> "cloudy"
}

object WeatherStore {
    private const val PREFS = "weather"
    private const val DEF_LAT = 38.2527
    private const val DEF_LON = -85.7585

    fun lat(context: Context): Double =
        SettingsStore.getString(context, PREFS, "lat")?.toDoubleOrNull() ?: DEF_LAT

    fun lon(context: Context): Double =
        SettingsStore.getString(context, PREFS, "lon")?.toDoubleOrNull() ?: DEF_LON

    fun setLocation(context: Context, lat: Double, lon: Double) {
        SettingsStore.edit(context, PREFS) {
            putString("lat", lat.toString())
            putString("lon", lon.toString())
        }
    }

    fun isDefaultLocation(context: Context): Boolean =
        SettingsStore.getString(context, PREFS, "lat") == null

    private fun file(context: Context): File =
        File(File(context.filesDir, "weather").apply { mkdirs() }, "today.json")

    fun cached(context: Context): WxToday? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching {
            val o = parseJson(f.readText()) as? JVal.Obj ?: return null
            WxToday(
                date = o.objStr("date") ?: return null,
                hiF = o.objNum("hi") ?: return null,
                loF = o.objNum("lo") ?: return null,
                code = o.objNum("code")?.toInt() ?: 3,
                fetchedAt = o.objStr("fetched") ?: "",
            )
        }.getOrNull()
    }

    fun needsRefresh(context: Context, today: LocalDate): Boolean {
        val c = cached(context) ?: return true
        if (c.date != today.toString()) return true
        val fetched = runCatching { java.time.Instant.parse(c.fetchedAt) }.getOrNull() ?: return true
        return java.time.Duration.between(fetched, java.time.Instant.now()).toHours() >= 1
    }

    suspend fun refresh(context: Context): WxToday? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=${lat(context)}&longitude=${lon(context)}" +
                    "&daily=temperature_2m_max,temperature_2m_min,weathercode&temperature_unit=fahrenheit" +
                    "&timezone=auto&forecast_days=2",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Houston-Android")
            }
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            conn.disconnect()
            if (status !in 200..299) throw IllegalStateException("Weather HTTP $status.")
            val wx = parseOpenMeteo(body) ?: throw IllegalStateException("Bad weather payload.")
            val f = file(context)
            f.writeText(
                "{\"date\":\"${escapeJson(wx.date)}\",\"hi\":${wx.hiF},\"lo\":${wx.loF}," +
                    "\"code\":${wx.code},\"fetched\":\"${escapeJson(wx.fetchedAt)}\"}",
            )
            wx
        }.getOrNull()
    }
}
