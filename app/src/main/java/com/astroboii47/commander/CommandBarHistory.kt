package com.astroboii47.commander

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray

/** Local history. Android only allows clipboard reads while Houston is foreground. */
object CommandBarHistory {
    private const val PREFS = "command_bar_history"
    private fun read(context: Context, key: String): List<String> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, 0).getString(key, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    private fun save(context: Context, key: String, value: String, max: Int) {
        if (value.isBlank()) return
        val items = (listOf(value.trim()) + read(context, key)).distinct().take(max)
        context.getSharedPreferences(PREFS, 0).edit().putString(key, JSONArray(items).toString()).apply()
    }
    fun searches(context: Context) = read(context, "searches")
    fun recordSearch(context: Context, query: String) = save(context, "searches", query, 30)
    fun clipboard(context: Context) = read(context, "clipboard")
    fun recordClipboard(context: Context) {
        // Android exposes the current clip only while our activity has focus.
        val manager = context.getSystemService(ClipboardManager::class.java)
        val item = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return
        val text = (item.text ?: item.htmlText)?.toString().orEmpty()
        if (text.length in 1..5000) save(context, "clipboard", text, 30)
    }
    fun copy(context: Context, value: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Houston", value))
        save(context, "clipboard", value, 30)
    }
    fun clearClipboard(context: Context) = context.getSharedPreferences(PREFS, 0).edit().remove("clipboard").apply()
    fun clearSearches(context: Context) = context.getSharedPreferences(PREFS, 0).edit().remove("searches").apply()
}
