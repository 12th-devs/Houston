package com.astroboii47.commander

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/**
 * Timer/alarm, calculator and unit-converter entry points.
 * Holds only applicationContext. Activity passed per launch call.
 */
class TimerCalcEngine(private val appContext: Context) {

    fun calculatorPreview(text: String): String? = TimerCalcLogic.calculatorPreview(text)

    fun setTimer(activity: Activity, text: String): String? {
        if (text.isBlank()) return "Type a duration after +"
        parseAlarmTime(text)?.let { alarm ->
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarm.hour24)
                putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Command alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            if (intent.resolveActivity(appContext.packageManager) == null) return "No alarm app is installed"
            return runCatching {
                activity.startActivity(intent)
                null
            }.getOrElse { error ->
                if (error is SecurityException) "Permission denied by the alarm app" else "Alarm could not be set"
            }
        }
        val match = Regex("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\\b").find(text)
            ?: return "Try +10 minutes tea or +6:30am"
        val amount = match.groupValues[1].toDoubleOrNull() ?: return "Invalid timer duration"
        val unit = match.groupValues[2].lowercase()
        val multiplier = when {
            unit.startsWith("h") -> 3600.0
            unit.startsWith("m") -> 60.0
            else -> 1.0
        }
        val seconds = (amount * multiplier).toInt().coerceAtLeast(1)
        val label = text.removeRange(match.range).trim().ifBlank { "Command timer" }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (intent.resolveActivity(appContext.packageManager) == null) return "No timer app is installed"
        return runCatching {
            activity.startActivity(intent)
            null
        }.getOrElse { error ->
            if (error is SecurityException) "Permission denied by the timer app" else "Timer could not be started"
        }
    }

    fun alarmTimeLabel(text: String): String? = TimerCalcLogic.alarmTimeLabel(text)

    fun timerInputPreview(text: String): String? = TimerCalcLogic.timerInputPreview(text)

    internal fun timerDurationSeconds(text: String): Long? = TimerCalcLogic.timerDurationSeconds(text)

    internal typealias AlarmTime = TimerCalcLogic.AlarmTime

    internal fun parseAlarmTime(text: String): TimerCalcLogic.AlarmTime? =
        TimerCalcLogic.parseAlarmTime(text)

    fun calculate(text: String): String? = TimerCalcLogic.calculate(text)

    internal fun evaluateCalculation(text: String): Pair<Double, String>? = TimerCalcLogic.evaluateCalculation(text)

    internal fun normalizeCalculation(text: String): String = TimerCalcLogic.normalizeCalculation(text)

    fun openTimer(activity: Activity): String? = startOrFallback(
        activity,
        Intent(AlarmClock.ACTION_SET_TIMER),
        null,
        "No timer app is installed",
    )

    private fun startOrFallback(activity: Activity, primary: Intent, fallback: Intent?, unavailable: String): String? {
        val pm = appContext.packageManager
        val chosen = if (primary.resolveActivity(pm) != null) primary else fallback
        if (chosen == null || chosen.resolveActivity(pm) == null) return unavailable
        return runCatching {
            startActivityAndFinish(activity, chosen)
            null
        }.getOrElse { error ->
            if (error is SecurityException) "Permission denied by the target app" else unavailable
        }
    }

    private fun startActivityAndFinish(activity: Activity, intent: Intent) {
        val launchOptions = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        activity.startActivity(intent, launchOptions)
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}

/** Pure timer/calc helpers (no Android deps) for unit testing. */
internal object TimerCalcLogic {
    fun calculatorPreview(text: String): String? = UnitConverter.convert(text) ?: evaluateCalculation(text)?.second

    fun alarmTimeLabel(text: String): String? = parseAlarmTime(text)?.label

    fun timerInputPreview(text: String): String? = alarmTimeLabel(text)?.let { "alarm · $it" }
        ?: timerDurationSeconds(text)?.let { seconds ->
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val remainder = seconds % 60
            "timer · ${if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, remainder) else "%02d:%02d".format(minutes, remainder)}"
        }

    fun timerDurationSeconds(text: String): Long? {
        val match = Regex("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\\b").find(text)
            ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        return when {
            match.groupValues[2].startsWith("h", true) -> (amount * 3600).toLong()
            match.groupValues[2].startsWith("m", true) -> (amount * 60).toLong()
            else -> amount.toLong()
        }.coerceAtLeast(1)
    }

    data class AlarmTime(val hour24: Int, val minute: Int, val label: String)

    fun parseAlarmTime(text: String): AlarmTime? {
        val match = Regex("(?i)^\\s*(\\d{1,2})(?::?(\\d{2}))?\\s*([ap])\\.?m\\.?\\s*$").matchEntire(text)
            ?: return null
        val hour12 = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val minute = match.groupValues[2].takeIf(String::isNotBlank)?.toIntOrNull() ?: 0
        if (minute !in 0..59) return null
        val afternoon = match.groupValues[3].equals("p", ignoreCase = true)
        val hour24 = when {
            afternoon && hour12 != 12 -> hour12 + 12
            !afternoon && hour12 == 12 -> 0
            else -> hour12
        }
        val suffix = if (afternoon) "PM" else "AM"
        return AlarmTime(hour24, minute, "%d:%02d %s".format(hour12, minute, suffix))
    }

    fun calculate(text: String): String? {
        if (text.isBlank()) return "Type a calculation after ,"
        val rendered = UnitConverter.convert(text) ?: evaluateCalculation(text)?.second ?: return "Invalid calculation"
        return "$text = $rendered"
    }

    fun evaluateCalculation(text: String): Pair<Double, String>? {
        val normalized = normalizeCalculation(text)
        val value = runCatching { ExpressionParser(normalized).parse() }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        val rendered = if (value % 1.0 == 0.0) value.toLong().toString() else "%.10f".format(value).trimEnd('0').trimEnd('.')
        return value to rendered
    }

    fun normalizeCalculation(text: String): String = text
        .lowercase()
        .replace(Regex("\\bmultiplied\\s+by\\b"), "*")
        .replace(Regex("\\btimes\\b"), "*")
        .replace(Regex("\\bdivided\\s+by\\b"), "/")
        .replace(Regex("\\bdivide\\b"), "/")
        .replace(Regex("\\bover\\b"), "/")
        .replace(Regex("\\bplus\\b|\\badd\\b"), "+")
        .replace(Regex("\\bminus\\b|\\bsubtract\\b"), "-")
        .replace('x', '*')
        .replace('×', '*')
        .replace('÷', '/')
}

private class ExpressionParser(private val source: String) {
    private var index = 0
    fun parse(): Double {
        val result = expression()
        skipSpaces()
        require(index == source.length)
        return result
    }
    private fun expression(): Double {
        var value = term()
        while (true) {
            skipSpaces()
            value = when {
                take('+') -> value + term()
                take('-') -> value - term()
                else -> return value
            }
        }
    }
    private fun term(): Double {
        var value = factor()
        while (true) {
            skipSpaces()
            value = when {
                take('*') -> value * factor()
                take('/') -> value / factor()
                else -> return value
            }
        }
    }
    private fun factor(): Double {
        skipSpaces()
        if (take('+')) return factor()
        if (take('-')) return -factor()
        if (take('(')) return expression().also { skipSpaces(); require(take(')')) }
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
        require(index > start)
        return source.substring(start, index).toDouble()
    }
    private fun take(char: Char): Boolean = if (index < source.length && source[index] == char) { index++; true } else false
    private fun skipSpaces() { while (index < source.length && source[index].isWhitespace()) index++ }
}
