package com.astroboii47.commander

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tagged diagnostics for the assistant invocation path.
 *
 * Every entry goes to logcat (`adb logcat -s HoustonAssistant`) AND to a
 * capped in-memory buffer surfaced in the app at Settings → Assistant →
 * "View assistant logs", where it can be copied to the clipboard. Volumes are
 * tiny (only on assistant invocation / role checks), so logging stays always
 * on. User text is never logged in full — only lengths plus a short truncated
 * preview.
 */
object AssistantLog {
    const val TAG = "HoustonAssistant"
    private const val MAX_ENTRIES = 400
    private const val MAX_MESSAGE_LENGTH = 600

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(val timeMillis: Long, val level: Level, val message: String)

    private val lock = Any()
    private val buffer = ArrayList<Entry>()

    fun d(message: String) {
        Log.d(TAG, message)
        store(Level.DEBUG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
        store(Level.INFO, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
        store(Level.WARN, if (error == null) message else "$message :: ${error.message}")
    }

    fun e(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message) else Log.e(TAG, message, error)
        store(Level.ERROR, if (error == null) message else "$message :: ${error.message}")
    }

    /** Length + truncated preview so private content never lands in full in logs. */
    fun preview(label: String, value: String?, max: Int = 80): String {
        if (value.isNullOrBlank()) return "$label=<empty>"
        val clean = value.replace(Regex("\\s+"), " ").trim()
        return "$label len=${clean.length} preview=\"${clean.take(max)}\""
    }

    fun entries(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun formatted(): String {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return synchronized(lock) {
            if (buffer.isEmpty()) return "No assistant log entries yet. Invoke the assistant, then refresh."
            buffer.joinToString("\n") { entry ->
                "${format.format(Date(entry.timeMillis))} ${entry.level.name.first()}/$TAG: ${entry.message}"
            }
        }
    }

    fun clear() {
        synchronized(lock) { buffer.clear() }
        Log.d(TAG, "In-app log buffer cleared")
    }

    fun copyToClipboard(context: Context): Boolean = runCatching {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Houston assistant log", formatted()))
        true
    }.getOrDefault(false)

    private fun store(level: Level, message: String) {
        synchronized(lock) {
            buffer.add(Entry(System.currentTimeMillis(), level, message.take(MAX_MESSAGE_LENGTH)))
            while (buffer.size > MAX_ENTRIES) buffer.removeAt(0)
        }
    }
}
