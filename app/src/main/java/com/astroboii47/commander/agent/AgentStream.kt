package com.astroboii47.commander.agent

import android.content.Context
import com.astroboii47.commander.ActionExecutor
import com.astroboii47.commander.ActionValidation
import com.astroboii47.commander.AiApi
import com.astroboii47.commander.AiProvider
import com.astroboii47.commander.ActionRegistry
import com.astroboii47.commander.AiSettings
import com.astroboii47.commander.AskMessage
import com.astroboii47.commander.AssistantTools
import com.astroboii47.commander.ExecutorOutcome
import com.astroboii47.commander.GeminiApi
import com.astroboii47.commander.untrustedToolBlock
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Live events for the Ask UI: token deltas, tool progress, approvals. */
sealed interface AgentStreamEvent {
    data class TextDelta(val text: String) : AgentStreamEvent
    data class StepDone(val toolName: String, val ok: Boolean, val message: String) : AgentStreamEvent
    data class NeedsApproval(val key: String, val reason: String, val level: AgentLevel) : AgentStreamEvent
    data class Done(val status: String) : AgentStreamEvent
}

/**
 * Streaming-capable agent turn. Providers with SSE (Gemini) stream the final
 * answer token-by-token; every other provider emits the full answer as one
 * delta after generation (progressive rendering, honestly labeled).
 * Tool steps stream as they execute. Cancellation is honored between rounds
 * and between stream chunks.
 */
suspend fun runAgentTurnStreaming(
    context: Context,
    prompt: String,
    history: List<AskMessage>,
    systemPrompt: String,
    confirmedKeys: Set<String>,
    maxSteps: Int = AssistantTools.MAX_STEPS,
    isCancelled: () -> Boolean = { false },
    onEvent: (AgentStreamEvent) -> Unit,
): AssistantTools.RunResult {
    ActionRegistry.registerDefaults()
    val provider = AiSettings.provider(context)
    suspend fun checkCancelled(): AssistantTools.RunResult? {
        if (isCancelled()) {
            onEvent(AgentStreamEvent.Done("cancelled"))
            return AssistantTools.RunResult("Cancelled.", emptyList(), emptyList(), false, provider.label)
        }
        coroutineContext.ensureActive()
        return null
    }
    checkCancelled()?.let { return it }

    // Native-tool providers keep the existing verified loop; emit post-hoc.
    if (AssistantTools.supportsNativeTools(provider)) {
        val result = AssistantTools.runConversation(context, prompt, history, confirmedKeys = confirmedKeys, maxSteps = maxSteps)
        result.steps.forEach { onEvent(AgentStreamEvent.StepDone(it.actionId, it.result.success, AgentEngine.sanitizeToolText(it.result.message))) }
        result.pending.forEach {
            val level = ActionRegistry.find(it.actionId)?.let(AgentEngine::levelFor) ?: AgentLevel.CONFIRM
            onEvent(AgentStreamEvent.NeedsApproval(it.key, it.reason, level))
        }
        onEvent(AgentStreamEvent.TextDelta(result.answer))
        onEvent(AgentStreamEvent.Done(if (result.pending.isNotEmpty()) "awaiting_approval" else "done"))
        return result
    }

    val conversation = (history + AskMessage("user", prompt)).toMutableList()
    val steps = ArrayList<AssistantTools.ExecutedStep>()
    val pending = ArrayList<AssistantTools.PendingConfirmation>()
    val canStream = provider == AiProvider.Gemini && AiSettings.apiKey(context, provider).isNotBlank()

    suspend fun finalText(extra: List<AskMessage>): String {
        val full = StringBuilder()
        if (canStream) {
            GeminiApi.streamGenerate(context, conversation + extra, systemPrompt) { delta ->
                full.append(delta)
                onEvent(AgentStreamEvent.TextDelta(delta))
            }.getOrThrow()
        } else {
            val text = AiApi.generateText(context, conversation + extra, systemPrompt).getOrThrow()
            full.append(text)
            onEvent(AgentStreamEvent.TextDelta(text))
        }
        return full.toString()
    }

    for (round in 0 until maxSteps) {
        checkCancelled()?.let { return it.copy(steps = steps, pending = pending) }
        val answer = AiApi.generateText(context, conversation, systemPrompt).getOrElse {
            onEvent(AgentStreamEvent.Done("failed"))
            return AssistantTools.RunResult(it.message ?: "${provider.label} request failed", steps, pending, false, provider.label)
        }
        val calls = AssistantTools.parseFallbackCalls(answer)
        if (calls.isEmpty()) {
            val prefix = if (steps.isNotEmpty()) steps.joinToString("\n") { it.result.message } + "\n" else ""
            if (prefix.isNotEmpty()) onEvent(AgentStreamEvent.TextDelta(prefix))
            finalText(emptyList())
            onEvent(AgentStreamEvent.Done("done"))
            return AssistantTools.RunResult((prefix + answer).trim(), steps, pending, false, provider.label)
        }
        var progressed = false
        for (call in calls) {
            val action = ActionRegistry.find(call.id) ?: continue
            when (val outcome = ActionExecutor.run(context, action, call.args, confirmedKeys)) {
                is ExecutorOutcome.Executed -> {
                    steps.add(AssistantTools.ExecutedStep(action.id, call.args, outcome.result))
                    onEvent(AgentStreamEvent.StepDone(action.name, outcome.result.success, AgentEngine.sanitizeToolText(outcome.result.message)))
                    conversation.add(AskMessage("assistant", answer))
                    conversation.add(AskMessage("tool", untrustedToolBlock(action.id, outcome.result.message)))
                    progressed = true
                }
                is ExecutorOutcome.NeedsConfirmation -> {
                    val key = ActionValidation.confirmationKey(action.id, call.args)
                    pending.add(
                        AssistantTools.PendingConfirmation(
                            action.id, action.name, call.args, key,
                            "${action.name} needs your confirmation before it runs.",
                        ),
                    )
                    onEvent(AgentStreamEvent.NeedsApproval(key, "${action.name} needs confirmation.", AgentEngine.levelFor(action)))
                }
                is ExecutorOutcome.MissingPermissions -> {
                    conversation.add(AskMessage("assistant", answer))
                    conversation.add(AskMessage("tool", untrustedToolBlock(action.id, "permission needed.")))
                    onEvent(AgentStreamEvent.StepDone(action.name, false, "Permission needed — grant it in settings."))
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
            onEvent(AgentStreamEvent.Done("awaiting_approval"))
            return AssistantTools.RunResult(pending.joinToString("\n") { it.reason }, steps, pending, false, provider.label)
        }
        if (!progressed) {
            onEvent(AgentStreamEvent.TextDelta(answer))
            onEvent(AgentStreamEvent.Done("done"))
            return AssistantTools.RunResult(answer, steps, pending, false, provider.label)
        }
        val lastFailed = steps.lastOrNull()?.let { !it.result.success } == true
        if (lastFailed) break
    }
    val closing = finalText(listOf(AskMessage("user", "Answer now using the tool results above.")))
    onEvent(AgentStreamEvent.Done("done"))
    return AssistantTools.RunResult(
        closing.ifBlank { steps.joinToString("\n") { it.result.message }.ifBlank { "Done." } },
        steps, pending, false, provider.label,
    )
}
