package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ActionCategory { READ_ONLY, REVERSIBLE, CONSEQUENTIAL }

enum class ParamType { TEXT, INT, NUMBER, BOOLEAN }

data class ParamSpec(
    val name: String,
    val type: ParamType,
    val required: Boolean = false,
    val min: Double = Double.NEGATIVE_INFINITY,
    val max: Double = Double.POSITIVE_INFINITY,
    val maxLength: Int = 500,
    val description: String = "",
)

data class ActionResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, String> = emptyMap(),
) {
    companion object {
        fun ok(message: String, data: Map<String, String> = emptyMap()) = ActionResult(true, message, data)
        fun fail(message: String, data: Map<String, String> = emptyMap()) = ActionResult(false, message, data)
    }
}

data class SystemAction(
    val id: String,
    val name: String,
    val description: String,
    val category: ActionCategory,
    val permissions: List<String> = emptyList(),
    val params: List<ParamSpec> = emptyList(),
    val aliases: Set<String> = emptySet(),
    val available: (Context) -> Boolean = { true },
    val execute: suspend (Context, Map<String, String>) -> ActionResult,
)

object ActionValidation {
    fun validate(action: SystemAction, raw: Map<String, String>): Result<Map<String, String>> = runCatching {
        val out = HashMap<String, String>()
        for (spec in action.params) {
            val value = raw[spec.name]?.trim()
            if (value.isNullOrEmpty()) {
                if (spec.required) throw IllegalArgumentException("Missing ${spec.name}")
                continue
            }
            when (spec.type) {
                ParamType.TEXT -> {
                    if (value.length > spec.maxLength) throw IllegalArgumentException("${spec.name} is too long")
                    out[spec.name] = value
                }
                ParamType.INT -> {
                    val number = value.toIntOrNull() ?: throw IllegalArgumentException("${spec.name} must be a whole number")
                    if (number < spec.min || number > spec.max) {
                        throw IllegalArgumentException("${spec.name} must be ${spec.min.toInt()}–${spec.max.toInt()}")
                    }
                    out[spec.name] = number.toString()
                }
                ParamType.NUMBER -> {
                    val number = value.toDoubleOrNull() ?: throw IllegalArgumentException("${spec.name} must be a number")
                    if (number < spec.min || number > spec.max) throw IllegalArgumentException("${spec.name} out of range")
                    out[spec.name] = number.toString()
                }
                ParamType.BOOLEAN -> {
                    val normalized = value.lowercase()
                    if (normalized !in setOf("true", "false", "1", "0", "on", "off", "yes", "no")) {
                        throw IllegalArgumentException("${spec.name} must be true or false")
                    }
                    out[spec.name] = (normalized in setOf("true", "1", "on", "yes")).toString()
                }
            }
        }
        out as Map<String, String>
    }

    fun confirmationKey(actionId: String, args: Map<String, String>): String {
        val canonical = args.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
        return "$actionId{$canonical}"
    }
}

object ActionRegistry {
    private val actions = linkedMapOf<String, SystemAction>()
    private val aliasIndex = HashMap<String, String>()

    fun register(action: SystemAction) {
        actions[action.id] = action
        aliasIndex[action.id] = action.id
        action.aliases.forEach { aliasIndex[it] = action.id }
    }

    fun find(idOrAlias: String): SystemAction? = actions[aliasIndex[idOrAlias]]

    fun all(): List<SystemAction> = actions.values.toList()

    fun search(query: String): List<SystemAction> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return all()
        return all().filter {
            it.name.contains(needle, true) || it.description.contains(needle, true) || it.id.contains(needle, true)
        }
    }

    fun isRegistered(): Boolean = actions.isNotEmpty()

    @Synchronized
    fun registerDefaults() {
        if (isRegistered()) return
        BuiltInActions.all().forEach(::register)
    }

    @Synchronized
    fun resetForTests() {
        actions.clear()
        aliasIndex.clear()
    }
}

fun missingPermissions(context: Context, action: SystemAction): List<String> =
    action.permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

fun requiresConfirmation(action: SystemAction): Boolean =
    action.category == ActionCategory.CONSEQUENTIAL

internal fun launchIntent(context: Context, intent: Intent): Boolean = runCatching {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    true
}.getOrDefault(false)

internal fun settingIntent(action: String): Intent =
    Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Minimal arithmetic evaluator (no external deps, no code execution). */
object CalcParser {
    fun evaluate(raw: String): String? {
        val normalized = raw.lowercase()
            .replace("multiplied by", "*").replace("times", "*")
            .replace("divided by", "/").replace("divide", "/").replace("over", "/")
            .replace("plus", "+").replace("add", "+").replace("minus", "-").replace("subtract", "-")
            .replace('x', '*').replace('×', '*').replace('÷', '/')
        if (!Regex("^[0-9\\s+\\-*/().%^]+$").matches(normalized)) return null
        if (!normalized.any(Char::isDigit) || !normalized.any { it in "+-*/%^()" }) return null
        val value = runCatching { Parser(normalized).parse() }.getOrNull() ?: return null
        if (!value.isFinite()) return null
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else "%.10f".format(value).trimEnd('0').trimEnd('.')
    }

    private class Parser(val source: String) {
        var index = 0
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
                    take('%') -> value % factor()
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
        private fun take(char: Char): Boolean =
            if (index < source.length && source[index] == char) { index++; true } else false
        private fun skipSpaces() { while (index < source.length && source[index].isWhitespace()) index++ }
    }
}

/** Executes a validated action; kept separate for testability of the policy. */
object ActionExecutor {
    suspend fun run(
        context: Context,
        action: SystemAction,
        rawArgs: Map<String, String>,
        confirmed: Set<String> = emptySet(),
    ): ExecutorOutcome {
        val args = ActionValidation.validate(action, rawArgs).getOrElse {
            return ExecutorOutcome.InvalidArgs(action, it.message ?: "Invalid arguments")
        }
        val missing = missingPermissions(context, action)
        if (missing.isNotEmpty()) {
            return ExecutorOutcome.MissingPermissions(action, args, missing)
        }
        if (!action.available(context)) {
            return ExecutorOutcome.Unavailable(action, args, "${action.name} is unavailable on this device")
        }
        if (requiresConfirmation(action) && ActionValidation.confirmationKey(action.id, args) !in confirmed) {
            return ExecutorOutcome.NeedsConfirmation(action, args)
        }
        return try {
            val result = withContext(Dispatchers.IO) { action.execute(context, args) }
            ExecutorOutcome.Executed(action, args, result)
        } catch (e: Exception) {
            ExecutorOutcome.Executed(action, args, ActionResult.fail(e.message ?: "Action failed"))
        }
    }

    suspend fun runLegacy(context: Context, tool: String, args: JSONObject): AiTools.Result {
        ActionRegistry.registerDefaults()
        val mapped = mapOf(
            "date" to "assistant.date", "web_search" to "web.search", "weather" to "assistant.weather",
            "search_files" to "files.search", "search_screenshots" to "files.search",
            "bluetooth_status" to "device.bluetooth.status", "bluetooth_devices" to "device.bluetooth.devices",
            "bluetooth_set" to "device.bluetooth.set", "volume_get" to "device.volume.get",
            "volume_set" to "device.volume.set", "brightness_get" to "device.brightness.get",
            "brightness_set" to "device.brightness.set", "music_status" to "media.status",
            "music_control" to "media.control",
        )
        if (tool == "screenshot") return AiTools.execute(context, org.json.JSONObject().put("tool", tool).put("args", args))
        val action = ActionRegistry.find(mapped[tool] ?: tool)
            ?: return AiTools.execute(context, org.json.JSONObject().put("tool", tool).put("args", args))
        val argMap = HashMap<String, String>()
        val keys = args.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            argMap[key] = args.opt(key)?.toString().orEmpty()
        }
        val outcome = run(context, action, argMap, confirmed = setOf(ActionValidation.confirmationKey(action.id, argMap)))
        return when (outcome) {
            is ExecutorOutcome.Executed -> AiTools.Result(outcome.result.message)
            is ExecutorOutcome.MissingPermissions -> AiTools.Result("Permission needed: ${outcome.missing.joinToString()}")
            is ExecutorOutcome.NeedsConfirmation -> AiTools.Result("Confirmation needed for ${action.name}")
            is ExecutorOutcome.InvalidArgs -> AiTools.Result("Tool error: ${outcome.reason}")
            is ExecutorOutcome.Unavailable -> AiTools.Result(outcome.reason)
        }
    }
}

sealed interface ExecutorOutcome {
    data class Executed(val action: SystemAction, val args: Map<String, String>, val result: ActionResult) : ExecutorOutcome
    data class NeedsConfirmation(val action: SystemAction, val args: Map<String, String>) : ExecutorOutcome
    data class MissingPermissions(val action: SystemAction, val args: Map<String, String>, val missing: List<String>) : ExecutorOutcome
    data class InvalidArgs(val action: SystemAction, val reason: String) : ExecutorOutcome
    data class Unavailable(val action: SystemAction, val args: Map<String, String>, val reason: String) : ExecutorOutcome
}

internal fun String.toBooleanStrictOrNull(): Boolean? = when (lowercase()) {
    "true", "1", "on", "yes" -> true
    "false", "0", "off", "no" -> false
    else -> null
}
