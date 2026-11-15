package com.astroboii47.commander

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Connects [ActionRegistry] to every AI provider.
 *
 * Two execution paths:
 * 1. Native function calling — OpenAI + OpenRouter via chat-completions `tools`.
 *    Gemini/Anthropic translators are provided ([geminiFunctionDeclarations],
 *    [anthropicTools]) but [supportsNativeTools] stays conservative until each
 *    provider path is verified on-device; they use path 2 meanwhile.
 * 2. Deterministic fallback — JSON tool calls in model text (same tolerant shape
 *    as the existing [AiTools] loop) plus [DeterministicCommands] for simple
 *    requests when the model has no usable tools.
 *
 * Every call is validated ([ActionValidation]), permission-checked and
 * confirmation-gated before it runs. At most [MAX_STEPS] actions run per request,
 * sequentially. Unknown ids, code, shell and raw intents never execute.
 */
object AssistantTools {
    const val MAX_STEPS = 5

    fun supportsNativeTools(provider: AiProvider): Boolean = when (provider) {
        AiProvider.OpenAI, AiProvider.OpenRouter -> true
        AiProvider.Gemini, AiProvider.Anthropic, AiProvider.Device, AiProvider.LiteRt -> false
    }

    // --- Consistent tool schema ------------------------------------------------

    /**
     * Plain-data schema (no org.json) so the translators stay JVM-unit-testable.
     * Network boundaries convert with `JSONObject(map)` / `JSONArray(list)`.
     */
    fun parameterSchema(spec: ParamSpec): Map<String, Any?> {
        val type = when (spec.type) {
            ParamType.TEXT -> "string"
            ParamType.INT -> "integer"
            ParamType.NUMBER -> "number"
            ParamType.BOOLEAN -> "boolean"
        }
        return mapOf("type" to type, "description" to spec.description.ifBlank { spec.name })
    }

    fun functionParameters(action: SystemAction): Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to action.params.associate { it.name to parameterSchema(it) },
        "required" to action.params.filter { it.required }.map { it.name },
    )

    /** OpenAI / OpenRouter chat-completions `tools` payload. */
    fun openAiToolsPayload(actions: List<SystemAction> = ActionRegistry.all()): List<Map<String, Any?>> =
        actions.map { action ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to action.id,
                    "description" to "${action.name}. ${action.description}",
                    "parameters" to functionParameters(action),
                ),
            )
        }

    /** Gemini `functionDeclarations` translation of the same schema. */
    fun geminiFunctionDeclarations(actions: List<SystemAction> = ActionRegistry.all()): List<Map<String, Any?>> =
        actions.map { action ->
            mapOf(
                "name" to action.id,
                "description" to "${action.name}. ${action.description}",
                "parameters" to functionParameters(action),
            )
        }

    /** Anthropic `tools` translation of the same schema. */
    fun anthropicTools(actions: List<SystemAction> = ActionRegistry.all()): List<Map<String, Any?>> =
        actions.map { action ->
            mapOf(
                "name" to action.id,
                "description" to "${action.name}. ${action.description}",
                "input_schema" to functionParameters(action),
            )
        }

    // --- Fallback JSON parsing (tolerant, registry-validated) ------------------
    //
    // Hand-rolled subset parser (no org.json): unit tests run against android.jar
    // stubs where org.json is unmocked, and the fallback shape is small — objects
    // with string values plus one nested flat args object.

    data class ParsedCall(val id: String, val args: Map<String, String>)

    fun parseFallbackCalls(answer: String): List<ParsedCall> {
        val clean = answer.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val objectTexts = if (clean.startsWith("[")) splitTopLevelObjects(clean) else listOf(clean)
        return objectTexts.take(MAX_STEPS).mapNotNull { parseOneCall(it) }
    }

    private fun parseOneCall(text: String): ParsedCall? {
        val trimmed = text.trim().takeIf { it.startsWith("{") && it.endsWith("}") } ?: return null
        val rawId = stringField(trimmed, "tool") ?: stringField(trimmed, "name") ?: return null
        if (rawId.isBlank()) return null
        val action = ActionRegistry.find(rawId.trim()) ?: return null
        val args = HashMap<String, String>()
        val argsBody = objectField(trimmed, "args") ?: objectField(trimmed, "arguments") ?: objectField(trimmed, "parameters")
        if (argsBody != null) {
            flatFields(argsBody).forEach { (key, value) -> args[key] = value }
        } else {
            // Flat form: {"tool":"device.volume.set","percent":40}
            action.params.forEach { spec ->
                scalarField(trimmed, spec.name)?.let { args[spec.name] = it }
            }
        }
        return ParsedCall(action.id, args)
    }

    private fun stringField(source: String, key: String): String? {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return pattern.find(source)?.groupValues?.get(1)?.let(::unescapeJsonString)
    }

    private fun scalarField(source: String, key: String): String? {
        stringField(source, key)?.let { return it }
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?|true|false|null)")
        return pattern.find(source)?.groupValues?.get(1)?.takeUnless { it == "null" }
    }

    private fun objectField(source: String, key: String): String? {
        val marker = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\\{").find(source) ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in marker.range.last until source.length) {
            val char = source[index]
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return source.substring(marker.range.last + 1, index)
                    }
                }
            }
        }
        return null
    }

    private fun flatFields(body: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val pattern = Regex("\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+(?:\\.\\d+)?|true|false|null))")
        pattern.findAll(body).forEach { match ->
            val key = match.groupValues[1]
            val raw = match.groupValues[2]
            out[key] = if (raw.startsWith("\"")) unescapeJsonString(match.groupValues[3]) else raw.takeUnless { it == "null" } ?: return@forEach
        }
        return out
    }

    private fun splitTopLevelObjects(array: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (index in array.indices) {
            val char = array[index]
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) start = index
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && start >= 0) {
                            out.add(array.substring(start, index + 1))
                            start = -1
                        }
                    }
                }
            }
        }
        return out
    }

    private fun unescapeJsonString(raw: String): String = buildString {
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                when (raw[index + 1]) {
                    '"', '\\', '/' -> append(raw[index + 1])
                    'n' -> append('\n')
                    't' -> append('\t')
                    'u' -> raw.substring(index + 2, (index + 6).coerceAtMost(raw.length))
                        .toIntOrNull(16)?.let { append(it.toChar()) }
                        ?.also { index += 4 } ?: append(raw[index + 1])
                    else -> append(raw[index + 1])
                }
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }

    fun fallbackInstructions(): String {
        ActionRegistry.registerDefaults()
        val lines = ActionRegistry.all().joinToString("\n") { action ->
            val params = action.params.joinToString(", ") {
                "${it.name}:${it.type.name.lowercase()}${if (it.required) "*" else ""}"
            }
            "- ${action.id}(${params.ifBlank { "no args" }}) — ${action.description}"
        }
        return """
            You can run Houston device actions. Reply ONLY with a JSON object for one call, or a JSON array for several sequential calls:
            [{"tool":"action.id","args":{"param":"value"}}]
            Available actions:
            $lines
            Only use listed action ids with their declared params. Consequential actions (calls, messages, sharing, tasks, events, deleting) need user confirmation: request them the same way and Houston will confirm first. After tool results arrive, answer normally. Do not invent results.
        """.trimIndent()
    }

    // --- Deterministic simple commands (no model tools needed) ------------------

    data class PlannedCall(val id: String, val args: Map<String, String>)

    /**
     * Safe, local parsing for the example requests in the product brief.
     * Returns validated plans only; anything ambiguous returns empty (model path).
     */
    fun deterministicPlan(request: String, sharedText: String? = null): List<PlannedCall> {
        val text = request.trim()
        if (text.isEmpty()) return emptyList()
        val lower = text.lowercase()
        // "Open Spotify and play music." → two sequential steps.
        Regex("(?i)^open\\s+(.+?)\\s+and\\s+(play|pause)\\s+music\\.?$").matchEntire(text)?.let { match ->
            val app = match.groupValues[1].trim()
            val musicAction = match.groupValues[2].lowercase()
            if (app.isNotBlank()) {
                return listOf(
                    PlannedCall("app.launch", mapOf("query" to app)),
                    PlannedCall("media.control", mapOf("action" to musicAction)),
                )
            }
        }
        Regex("(?i)^(turn\\s+on|switch\\s+on|enable)\\s+(my\\s+)?(flashlight|torch|flash)\\.?$").matchEntire(text)?.let {
            return listOf(PlannedCall("device.flashlight.toggle", emptyMap()))
        }
        Regex("(?i)^(turn\\s+off|switch\\s+off|disable)\\s+(my\\s+)?(flashlight|torch|flash)\\.?$").matchEntire(text)?.let {
            return listOf(PlannedCall("device.flashlight.toggle", emptyMap()))
        }
        Regex("(?i)^set\\s+(?:my\\s+|the\\s+)?brightness\\s+to\\s+(\\d{1,3})\\s*(?:%|percent)?\\.?$").matchEntire(text)?.let {
            val percent = it.groupValues[1].toIntOrNull()?.coerceIn(0, 100) ?: return emptyList()
            return listOf(PlannedCall("device.brightness.set", mapOf("percent" to percent.toString())))
        }
        Regex("(?i)^set\\s+(?:my\\s+|the\\s+)?(volume|media\\s+volume)\\s+to\\s+(\\d{1,3})\\s*(?:%|percent)?\\.?$").matchEntire(text)?.let {
            val percent = it.groupValues[2].toIntOrNull()?.coerceIn(0, 100) ?: return emptyList()
            return listOf(PlannedCall("device.volume.set", mapOf("percent" to percent.toString())))
        }
        Regex("(?i)^(open|launch|start)\\s+(.+?)\\.?$").matchEntire(text)?.let {
            val app = it.groupValues[2].trim().removeSuffix("app").trim()
            if (app.isNotBlank() && !app.contains("settings", true)) {
                return listOf(PlannedCall("app.launch", mapOf("query" to app)))
            }
        }
        Regex("(?i)^(call|phone|ring)\\s+([\\p{L} .'-]+)\\.?$").matchEntire(text)?.let {
            val who = it.groupValues[2].trim()
            if (who.isNotBlank()) return listOf(PlannedCall("comms.call", mapOf("query" to who)))
        }
        Regex("(?i)^text\\s+([\\p{L} .'-]+?)\\s+(that|saying)\\s+(.+)$", RegexOption.DOT_MATCHES_ALL).matchEntire(text)?.let {
            val who = it.groupValues[1].trim()
            val body = it.groupValues[3].trim().trimEnd('.')
            if (who.isNotBlank() && body.isNotBlank()) {
                return listOf(PlannedCall("comms.message.compose", mapOf("query" to who, "body" to body)))
            }
        }
        Regex("(?i)^remind\\s+me\\s+to\\s+(.+)$", RegexOption.DOT_MATCHES_ALL).matchEntire(text)?.let {
            val task = it.groupValues[1].trim().trimEnd('.')
            if (task.isNotBlank()) return listOf(PlannedCall("tasks.todo.add", mapOf("text" to task)))
        }
        Regex("(?i)^(?:create|set|start)\\s+(?:a\\s+)?(\\d+(?:\\.\\d+)?)[\\s-]*(second|minute|hour)[s]?\\s*timer(?:\\s+(?:for\\s+)?(.+))?\\.?$").matchEntire(text)?.let {
            val amount = it.groupValues[1].toDoubleOrNull() ?: return emptyList()
            val seconds = when {
                it.groupValues[2].startsWith("hour", true) -> (amount * 3600).toInt()
                it.groupValues[2].startsWith("minute", true) -> (amount * 60).toInt()
                else -> amount.toInt()
            }.coerceIn(1, 86400)
            val label = it.groupValues[3].trim().trimEnd('.')
            return listOf(
                PlannedCall(
                    "timer.create",
                    buildMap {
                        put("seconds", seconds.toString())
                        if (label.isNotBlank()) put("label", label)
                    },
                ),
            )
        }
        Regex("(?i)^show\\s+(me\\s+)?(my\\s+)?recent\\s+messages?(\\s+from\\s+([\\p{L} .'-]+))?\\.?$").matchEntire(text)?.let {
            return listOf(PlannedCall("comms.recent", emptyMap()))
        }
        if (!sharedText.isNullOrBlank()) {
            Regex("(?i)^(summarize|explain|copy|search).{0,80}(this|screen|page).*$", RegexOption.DOT_MATCHES_ALL)
                .matches(text)
                .takeIf { b -> b }
                ?.let { return listOf(PlannedCall("assistant.date", emptyMap())) }
            // Shared content without a device action: no plan (conversational answer).
            return emptyList()
        }
        return emptyList()
    }

    // --- Runner ------------------------------------------------------------------

    data class ExecutedStep(val actionId: String, val args: Map<String, String>, val result: ActionResult)
    data class PendingConfirmation(
        val actionId: String,
        val actionName: String,
        val args: Map<String, String>,
        val key: String,
        val reason: String,
    )
    data class RunResult(
        val answer: String,
        val steps: List<ExecutedStep>,
        val pending: List<PendingConfirmation>,
        val usedNativeTools: Boolean,
        val providerLabel: String,
    )

    /**
     * Full pipeline for one user request: plan (deterministic, then model),
     * validate, gate (permissions/confirmation/availability), execute ≤5
     * sequentially, and report the actual outcome.
     */
    suspend fun runConversation(
        context: Context,
        prompt: String,
        history: List<AskMessage>,
        assistContext: AssistantContext? = null,
        confirmedKeys: Set<String> = emptySet(),
        maxSteps: Int = MAX_STEPS,
    ): RunResult = withContext(Dispatchers.IO) {
        val stepCap = maxSteps.coerceIn(1, MAX_STEPS)
        ActionRegistry.registerDefaults()
        val provider = AiSettings.provider(context)
        AssistantLog.i(
            "Assistant request provider=${provider.label} history=${history.size} " +
                "shared=${assistContext?.sharedText?.isNotBlank() == true} nativeTools=${supportsNativeTools(provider)}",
        )
        val requestText = buildString {
            append(prompt.trim())
            val shared = assistContext?.sharedText?.trim().orEmpty()
            if (shared.isNotBlank() && !prompt.contains(shared)) {
                append("\n\nShared content: ${shared.take(2000)}")
            }
            assistContext?.link?.takeIf { it.isNotBlank() }?.let { append("\nLink: $it") }
        }
        // 1. Fast deterministic path (works even when the model has no tools).
        val deterministic = deterministicPlan(prompt, assistContext?.sharedText)
        if (deterministic.isNotEmpty()) {
            return@withContext executePlan(context, deterministic, confirmedKeys, provider.label, usedNativeTools = false, maxSteps = stepCap)
        }
        // 2. Native tools where supported.
        if (supportsNativeTools(provider)) {
            val native = runNativeLoop(context, requestText, history, confirmedKeys, provider, stepCap)
            if (native != null) return@withContext native
        }
        // 3. Fallback: model JSON tool calls.
        runFallbackLoop(context, requestText, history, confirmedKeys, provider, stepCap)
    }

    private suspend fun executePlan(
        context: Context,
        plan: List<PlannedCall>,
        confirmedKeys: Set<String>,
        providerLabel: String,
        usedNativeTools: Boolean,
        maxSteps: Int = MAX_STEPS,
    ): RunResult {
        val steps = ArrayList<ExecutedStep>()
        val pending = ArrayList<PendingConfirmation>()
        for (planned in plan.take(maxSteps.coerceIn(1, MAX_STEPS))) {
            val action = ActionRegistry.find(planned.id)
                ?: return RunResult(
                    "I couldn't run ${planned.id}: unknown action.",
                    steps, pending, usedNativeTools, providerLabel,
                )
            when (val outcome = ActionExecutor.run(context, action, planned.args, confirmedKeys)) {
                is ExecutorOutcome.Executed -> steps.add(ExecutedStep(action.id, planned.args, outcome.result))
                is ExecutorOutcome.NeedsConfirmation -> pending.add(
                    PendingConfirmation(
                        action.id, action.name, planned.args,
                        ActionValidation.confirmationKey(action.id, planned.args),
                        "${action.name} needs your confirmation before it runs.",
                    ),
                )
                is ExecutorOutcome.MissingPermissions -> return RunResult(
                    "${action.name} needs permission: ${outcome.missing.joinToString()}. Grant it in Houston settings, then ask again.",
                    steps, pending, usedNativeTools, providerLabel,
                )
                is ExecutorOutcome.InvalidArgs -> return RunResult(
                    "I couldn't run ${action.name}: ${outcome.reason}.",
                    steps, pending, usedNativeTools, providerLabel,
                )
                is ExecutorOutcome.Unavailable -> return RunResult(
                    outcome.reason,
                    steps, pending, usedNativeTools, providerLabel,
                )
            }
            // Stop after a failure or a pending confirmation; report honestly.
            val last = steps.lastOrNull()
            if (last != null && !last.result.success) break
            if (pending.isNotEmpty()) break
        }
        val answer = when {
            pending.isNotEmpty() -> pending.joinToString("\n") { it.reason }
            steps.isEmpty() -> "I couldn't run that action."
            else -> steps.joinToString("\n") { it.result.message }
        }
        return RunResult(answer, steps, pending, usedNativeTools, providerLabel)
    }

    private suspend fun runNativeLoop(
        context: Context,
        requestText: String,
        history: List<AskMessage>,
        confirmedKeys: Set<String>,
        provider: AiProvider,
        maxSteps: Int = MAX_STEPS,
    ): RunResult? {
        return try {
            val conversation = (history + AskMessage("user", requestText)).toMutableList()
            val steps = ArrayList<ExecutedStep>()
            val pending = ArrayList<PendingConfirmation>()
            repeat(maxSteps.coerceIn(1, MAX_STEPS)) {
                val step = AiApi.chatWithNativeToolsOnce(context, conversation, org.json.JSONArray(openAiToolsPayload()), provider)
                    .getOrNull() ?: return null
                when (step) {
                    is AiApi.NativeStep.Text -> {
                        if (steps.isEmpty() && pending.isEmpty()) {
                            // Model answered directly; check for fallback calls it may have
                            // embedded (some models ignore `tools` and emit JSON text).
                            val parsed = parseFallbackCalls(step.text)
                            if (parsed.isEmpty()) {
                                return RunResult(step.text, steps, pending, true, provider.label)
                            }
                            val plan = parsed.map { PlannedCall(it.id, it.args) }
                            val outcome = executePlan(context, plan, confirmedKeys, provider.label, true, maxSteps)
                            if (outcome.steps.isNotEmpty() || outcome.pending.isNotEmpty()) return outcome
                            return RunResult(step.text, steps, pending, true, provider.label)
                        }
                        conversation.add(AskMessage("assistant", step.text))
                        return RunResult(
                            (steps.joinToString("\n") { it.result.message } + "\n" + step.text).trim(),
                            steps, pending, true, provider.label,
                        )
                    }
                    is AiApi.NativeStep.ToolCalls -> {
                        if (step.calls.isEmpty()) return null
                        var progressed = false
                        for (call in step.calls.take(maxSteps.coerceIn(1, MAX_STEPS) - steps.size - pending.size).ifEmpty { return null }) {
                            val action = ActionRegistry.find(call.name)
                                if (action == null) {
                                    conversation.add(
                                        AskMessage("tool", untrustedToolBlock(call.name, "unknown action ${call.name}")),
                                    )
                                    progressed = true
                                    continue
                                }
                            val args = call.arguments.entries.associate { it.key to it.value }
                            when (val outcome = ActionExecutor.run(context, action, args, confirmedKeys)) {
                                is ExecutorOutcome.Executed -> {
                                    steps.add(ExecutedStep(action.id, args, outcome.result))
                                    conversation.add(
                                        AskMessage("tool", untrustedToolBlock(action.id, outcome.result.message)),
                                    )
                                    progressed = true
                                }
                                is ExecutorOutcome.NeedsConfirmation -> {
                                    pending.add(
                                        PendingConfirmation(
                                            action.id, action.name, args,
                                            ActionValidation.confirmationKey(action.id, args),
                                            "${action.name} needs your confirmation before it runs.",
                                        ),
                                    )
                                }
                                is ExecutorOutcome.MissingPermissions -> {
                                    conversation.add(
                                        AskMessage(
                                            "tool",
                                            untrustedToolBlock(action.id, "permission needed: ${outcome.missing.joinToString()}"),
                                        ),
                                    )
                                    progressed = true
                                }
                                is ExecutorOutcome.InvalidArgs -> {
                                    conversation.add(
                                        AskMessage("tool", untrustedToolBlock(action.id, "invalid args: ${outcome.reason}")),
                                    )
                                    progressed = true
                                }
                                is ExecutorOutcome.Unavailable -> {
                                    conversation.add(
                                        AskMessage("tool", untrustedToolBlock(action.id, outcome.reason)),
                                    )
                                    progressed = true
                                }
                            }
                            if (pending.isNotEmpty()) break
                        }
                        if (pending.isNotEmpty()) {
                            return RunResult(
                                pending.joinToString("\n") { it.reason },
                                steps, pending, true, provider.label,
                            )
                        }
                        if (!progressed) return null
                    }
                }
            }
            val closing = AiApi.generateText(
                context,
                conversation + AskMessage("user", "Answer now using the tool results above."),
            ).getOrNull()?.ifBlank { null }
                ?: steps.joinToString("\n") { it.result.message }.ifBlank { "Done." }
            RunResult(closing, steps, pending, true, provider.label)
        } catch (e: Exception) {
            AssistantLog.w("runReactLoop failed", e)
            null
        }
    }

    private suspend fun runFallbackLoop(
        context: Context,
        requestText: String,
        history: List<AskMessage>,
        confirmedKeys: Set<String>,
        provider: AiProvider,
        maxSteps: Int = MAX_STEPS,
    ): RunResult {
        val systemPrompt = AiSettings.systemPrompt(context) + "\n\n" + fallbackInstructions()
        val conversation = (history + AskMessage("user", requestText)).toMutableList()
        val steps = ArrayList<ExecutedStep>()
        val pending = ArrayList<PendingConfirmation>()
        for (round in 0 until maxSteps.coerceIn(1, MAX_STEPS)) {
            val answer = AiApi.generateText(context, conversation, systemPrompt).getOrElse {
                return RunResult(it.message ?: "${provider.label} request failed", steps, pending, false, provider.label)
            }
            val calls = parseFallbackCalls(answer)
            if (calls.isEmpty()) {
                val prefix = if (steps.isNotEmpty()) steps.joinToString("\n") { it.result.message } + "\n" else ""
                return RunResult((prefix + answer).trim(), steps, pending, false, provider.label)
            }
            var progressed = false
            for (call in calls) {
                val action = ActionRegistry.find(call.id) ?: continue
                when (val outcome = ActionExecutor.run(context, action, call.args, confirmedKeys)) {
                    is ExecutorOutcome.Executed -> {
                        steps.add(ExecutedStep(action.id, call.args, outcome.result))
                        conversation.add(AskMessage("assistant", answer))
                        conversation.add(AskMessage("tool", untrustedToolBlock(action.id, outcome.result.message)))
                        progressed = true
                    }
                    is ExecutorOutcome.NeedsConfirmation -> pending.add(
                        PendingConfirmation(
                            action.id, action.name, call.args,
                            ActionValidation.confirmationKey(action.id, call.args),
                            "${action.name} needs your confirmation before it runs.",
                        ),
                    )
                    is ExecutorOutcome.MissingPermissions -> {
                        conversation.add(
                            AskMessage("assistant", answer),
                        )
                        conversation.add(
                            AskMessage("tool", untrustedToolBlock(action.id, "permission needed. Ask the user to grant it in Houston settings.")),
                        )
                        progressed = true
                    }
                    is ExecutorOutcome.InvalidArgs -> {
                        conversation.add(AskMessage("assistant", answer))
                        conversation.add(AskMessage("tool", untrustedToolBlock(action.id, "invalid args: ${outcome.reason}")))
                        progressed = true
                    }
                    is ExecutorOutcome.Unavailable -> {
                        conversation.add(AskMessage("assistant", answer))
                        conversation.add(AskMessage("tool", untrustedToolBlock(action.id, outcome.reason)))
                        progressed = true
                    }
                }
                if (pending.isNotEmpty()) break
            }
            if (pending.isNotEmpty()) {
                return RunResult(pending.joinToString("\n") { it.reason }, steps, pending, false, provider.label)
            }
            if (!progressed) {
                return RunResult(answer, steps, pending, false, provider.label)
            }
            val lastFailed = steps.lastOrNull()?.let { !it.result.success } == true
            if (lastFailed) break
        }
        val closing = AiApi.generateText(
            context,
            conversation + AskMessage("user", "Answer now using the tool results above."),
        ).getOrNull()?.ifBlank { null }
            ?: steps.joinToString("\n") { it.result.message }.ifBlank { "Done." }
        return RunResult(closing, steps, pending, false, provider.label)
    }
}
