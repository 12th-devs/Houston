package com.astroboii47.commander

/**
 * Lightweight, fully local intent detection.
 *
 * Recognizes common query shapes without requiring users to memorize triggers:
 * calculator expressions, timer phrases, unit conversions, "<service> <query>"
 * web shortcuts, settings/control keywords and the downloads folder.
 *
 * Explicit trigger prefixes (@ # - ! . + * , / ? and the file-search trigger,
 * default space) are NEVER reinterpreted: [parse] returns [SearchIntent.Trigger]
 * for them so the existing fast, predictable filters keep working.
 */
sealed interface SearchIntent {
    /** Plain search with no detected shortcut. Rank all sources normally. */
    data object Plain : SearchIntent

    /** First character is an explicit trigger; the rest is the scoped query. */
    data class Trigger(val symbol: Char, val rest: String) : SearchIntent

    /** Arithmetic expression such as "15 * 24" or "(2+3)*4". */
    data class Calculator(val expression: String) : SearchIntent

    /** Unit conversion such as "5 km in miles". Preview via [UnitConverter]. */
    data class UnitConversion(val expression: String) : SearchIntent

    /** Duration phrase such as "5 minute timer" or "timer 10m tea". */
    data class Timer(val seconds: Long, val label: String, val expression: String) : SearchIntent

    /** "<service> <query>" without needing the alias trigger, e.g. "youtube cats". */
    data class WebService(val targetId: String, val query: String) : SearchIntent

    /** Query looks like a settings/control request ("bluetooth", "brightness 50"). */
    data class SystemHint(val domain: String) : SearchIntent

    /** Query refers to the downloads folder. */
    data class Downloads(val query: String) : SearchIntent

    /** Single-name query likely to be a person ("mom"). Boost contacts. */
    data class PersonHint(val name: String) : SearchIntent
}

object SearchIntentParser {
    /** Trigger symbols preserved as explicit filters. File trigger is configurable (default space). */
    fun triggerSymbols(fileTrigger: Char = ' ', noteSymbol: Char = '!'): Set<Char> = buildSet {
        add('@')
        add('#')
        add('-')
        add(noteSymbol)
        add('+')
        add('*')
        add(',')
        add('/')
        add('?')
        add(fileTrigger)
    }

    fun isExplicitTrigger(query: String, fileTrigger: Char = ' ', noteSymbol: Char = '!'): Boolean {
        if (query.isEmpty()) return false
        return query.first() in triggerSymbols(fileTrigger, noteSymbol)
    }

    fun parse(raw: String, fileTrigger: Char = ' ', noteSymbol: Char = '!'): SearchIntent {
        if (raw.isEmpty()) return SearchIntent.Plain
        val first = raw.first()
        if (first in triggerSymbols(fileTrigger, noteSymbol)) {
            return SearchIntent.Trigger(first, raw.drop(1).trim())
        }
        val query = raw.trim()
        if (query.isEmpty()) return SearchIntent.Plain

        unitConversionIntent(query)?.let { return it }
        calculatorIntent(query)?.let { return it }
        timerIntent(query)?.let { return it }
        webServiceIntent(query)?.let { return it }
        downloadsIntent(query)?.let { return it }
        systemHintIntent(query)?.let { return it }
        personHintIntent(query)?.let { return it }
        return SearchIntent.Plain
    }

    private val unitConversionPattern =
        Regex("^\\s*(-?\\d+(?:[.,]\\d+)?)\\s*(.+?)\\s+(?:in|to|into|as)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)

    fun unitConversionIntent(query: String): SearchIntent.UnitConversion? {
        val trimmed = query.trim()
        if (trimmed.length < 6 || !unitConversionPattern.matches(trimmed)) return null
        if (!trimmed.any(Char::isDigit)) return null
        return SearchIntent.UnitConversion(trimmed)
    }

    // --- Calculator: at least one digit and one operator, only math chars. ---
    private val calculatorChars = Regex("^[0-9\\s+\\-*/x×÷().%^]+$")
    private val calculatorOperator = Regex("[+\\-*/x×÷%^()]")

    fun calculatorIntent(query: String): SearchIntent.Calculator? {
        val trimmed = query.trim()
        if (trimmed.length < 3) return null
        if (!calculatorChars.matches(trimmed)) return null
        if (!trimmed.any(Char::isDigit)) return null
        if (!calculatorOperator.containsMatchIn(trimmed)) return null
        // Avoid treating a bare year/phone fragment ("2024", "+1") as math.
        val operators = calculatorOperator.findAll(trimmed).count()
        if (operators == 0) return null
        // A lone leading "+" with words is a timer trigger, not math (handled earlier by trigger).
        return SearchIntent.Calculator(trimmed)
    }

    // --- Timer: duration + timer/alarm/countdown, or "timer <duration> [label]". ---
    private val durationPattern =
        Regex("(?i)\\b(\\d+(?:\\.\\d+)?)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h)\\b")
    private val timerWord = Regex("(?i)\\b(timer|alarm|countdown)\\b")

    fun timerIntent(query: String): SearchIntent.Timer? {
        val match = durationPattern.find(query) ?: return null
        val mentionsTimer = timerWord.containsMatchIn(query) ||
            query.trim().startsWith("timer", ignoreCase = true)
        if (!mentionsTimer) return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val seconds = when {
            match.groupValues[2].startsWith("h", true) -> (amount * 3600).toLong()
            match.groupValues[2].startsWith("m", true) -> (amount * 60).toLong()
            else -> amount.toLong()
        }.coerceAtLeast(1)
        val label = query.removeRange(match.range)
            .replace(timerWord, " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return SearchIntent.Timer(seconds, label.ifBlank { "Houston timer" }, query.trim())
    }

    // --- "<service> <query>" shortcuts reusing existing alias targets. ---
    private val serviceWords = mapOf(
        "youtube" to "youtube",
        "yt" to "youtube",
        "maps" to "maps",
        "map" to "maps",
        "waze" to "waze",
        "gmail" to "gmail",
        "mail" to "gmail",
        "reddit" to "reddit",
        "spotify" to "spotify",
        "drive" to "drive",
        "photos" to "photos",
        "ebay" to "ebay",
        "amazon" to "amazon",
        "play store" to "play",
        "playstore" to "play",
    )

    fun webServiceIntent(query: String): SearchIntent.WebService? {
        val lower = query.trim().lowercase()
        for ((word, targetId) in serviceWords) {
            if (lower == word) return null // Bare "spotify" should open the app, not a web search.
            if (lower.startsWith("$word ")) {
                val rest = query.trim().substring(word.length).trim()
                if (rest.isNotBlank()) return SearchIntent.WebService(targetId, rest)
            }
        }
        return null
    }

    private fun downloadsIntent(query: String): SearchIntent.Downloads? {
        val lower = query.trim().lowercase()
        if (lower == "downloads" || lower == "download") return SearchIntent.Downloads("")
        if (lower.startsWith("downloads ") || lower.startsWith("download ")) {
            return SearchIntent.Downloads(query.trim().substringAfter(' ').trim())
        }
        return null
    }

    private val systemDomains = listOf(
        "bluetooth" to "bluetooth",
        "wifi" to "wifi",
        "wi-fi" to "wifi",
        "battery" to "battery",
        "brightness" to "brightness",
        "volume" to "volume",
        "sound" to "sound",
        "display" to "display",
        "notification" to "notifications",
        "flashlight" to "flashlight",
        "torch" to "flashlight",
        "rotation" to "rotation",
        "airplane" to "airplane",
        "location" to "location",
        "gps" to "location",
        "storage" to "storage",
        "language" to "language",
        "keyboard" to "keyboard",
        "theme" to "theme",
        "dark mode" to "theme",
        "ringer" to "ringer",
        "dnd" to "ringer",
        "qr" to "qr",
    )

    fun systemHintIntent(query: String): SearchIntent.SystemHint? {
        val lower = query.trim().lowercase()
        for ((word, domain) in systemDomains) {
            if (lower == word || lower.startsWith("$word ") || lower.endsWith(" $word") || " $word " in " $lower ") {
                return SearchIntent.SystemHint(domain)
            }
        }
        return null
    }

    private val personPattern = Regex("^[\\p{L}][\\p{L} .'-]{1,40}$")

    fun personHintIntent(query: String): SearchIntent.PersonHint? {
        val trimmed = query.trim()
        if (' ' in trimmed && trimmed.split(' ').size > 3) return null
        if (!personPattern.matches(trimmed)) return null
        val lower = trimmed.lowercase()
        // Avoid stealing obvious non-person queries handled above.
        if (lower in setOf("timer", "alarm", "calculator", "settings", "downloads", "weather", "music")) return null
        if (lower.contains("timer") || lower.contains("alarm")) return null
        if (calculatorIntent(trimmed) != null) return null
        return SearchIntent.PersonHint(trimmed)
    }
}
