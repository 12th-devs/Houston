package com.astroboii47.commander.secondbrain.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astroboii47.commander.AskMessage
import com.astroboii47.commander.agent.AgentEngine
import com.astroboii47.commander.agent.AgentLevel
import com.astroboii47.commander.agent.AgentStreamEvent
import com.astroboii47.commander.agent.runAgentTurnStreaming
import com.astroboii47.commander.secondbrain.context.BrainContext
import com.astroboii47.commander.secondbrain.db.ChatMessage
import com.astroboii47.commander.secondbrain.db.ChatThread
import com.astroboii47.commander.secondbrain.di.BrainGraph
import com.astroboii47.commander.secondbrain.di.BrainPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AskUiMessage(
    val id: String,
    val role: String,
    val text: String,
    val streaming: Boolean = false,
    val toolNote: String? = null,
    val sources: List<String> = emptyList(),
)

data class PendingApprovalUi(val key: String, val reason: String, val level: AgentLevel)

/**
 * Conversation state for Ask. Threads + messages persist in Room; the
 * current answer streams in. Cancel stops orchestration between steps.
 */
class AskViewModel(app: Application) : AndroidViewModel(app) {
    private val db = BrainGraph.db(app)
    private var job: Job? = null
    private var confirmed = HashSet<String>()

    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    private val _threadId = MutableStateFlow<String?>(null)
    val threadId: StateFlow<String?> = _threadId.asStateFlow()

    private val _messages = MutableStateFlow<List<AskUiMessage>>(emptyList())
    val messages: StateFlow<List<AskUiMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingApprovalUi>>(emptyList())
    val pending: StateFlow<List<PendingApprovalUi>> = _pending.asStateFlow()

    private val _usage = MutableStateFlow<Triple<Long, Long, Long>?>(null)
    val usage: StateFlow<Triple<Long, Long, Long>?> = _usage.asStateFlow()

    init {
        refreshThreads()
    }

    fun refreshThreads() {
        viewModelScope.launch {
            _threads.value = db.chat().threads(30)
            _usage.value = BrainPrefs.usageSnapshot(getApplication())
        }
    }

    fun newThread() {
        job?.cancel()
        _threadId.value = null
        _messages.value = emptyList()
        _pending.value = emptyList()
        _error.value = null
        confirmed = HashSet()
    }

    fun openThread(id: String) {
        job?.cancel()
        viewModelScope.launch {
            val msgs = db.chat().messages(id)
            _threadId.value = id
            _messages.value = msgs.map {
                AskUiMessage(it.id, it.role, it.text, sources = parseSources(it.sources))
            }
            _pending.value = emptyList()
            _error.value = null
            confirmed = HashSet()
        }
    }

    fun deleteThread(id: String) {
        viewModelScope.launch {
            db.chat().deleteMessages(id)
            db.chat().deleteThread(id)
            if (_threadId.value == id) newThread()
            refreshThreads()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _busy.value = false
        _messages.value = _messages.value.map { if (it.streaming) it.copy(streaming = false) else it }
    }

    fun approve(key: String) {
        confirmed.add(key)
        _pending.value = _pending.value.filter { it.key != key }
        // Re-run the last user message with the new confirmation.
        val lastUser = _messages.value.lastOrNull { it.role == "user" }?.text ?: return
        send(lastUser, prefill = false)
    }

    fun dismissApproval(key: String) {
        _pending.value = _pending.value.filter { it.key != key }
    }

    fun send(text: String, prefill: Boolean = true) {
        val clean = text.trim()
        if (clean.isEmpty() || _busy.value) return
        job?.cancel()
        _error.value = null
        job = viewModelScope.launch {
            _busy.value = true
            try {
                val context = getApplication<Application>()
                var tid = _threadId.value
                if (tid == null) {
                    val thread = ChatThread(title = clean.take(48))
                    db.chat().upsertThread(thread)
                    tid = thread.id
                    _threadId.value = tid
                    refreshThreads()
                }
                val threadId = tid
                if (prefill) {
                    appendMessage(threadId, "user", clean)
                }
                val snapshot = BrainContext.snapshot(context, clean)
                val history = db.chat().messages(threadId)
                    .takeLast(10).map { AskMessage(it.role, it.text) }
                val streamId = "stream-${System.currentTimeMillis()}"
                appendUi(AskUiMessage(streamId, "assistant", "", streaming = true))
                val answerText = StringBuilder()
                val result = runAgentTurnStreaming(
                    context = context,
                    prompt = clean,
                    history = history,
                    systemPrompt = AgentEngine.groundedSystemPrompt(snapshot),
                    confirmedKeys = confirmed,
                    isCancelled = { !jobActive() },
                    onEvent = { ev ->
                        when (ev) {
                            is AgentStreamEvent.TextDelta -> {
                                answerText.append(ev.text)
                                updateUi(streamId) { it.copy(text = answerText.toString()) }
                            }
                            is AgentStreamEvent.StepDone -> {
                                appendToolNote(ev.toolName, ev.ok, ev.message)
                            }
                            is AgentStreamEvent.NeedsApproval -> {
                                _pending.value = _pending.value + PendingApprovalUi(ev.key, ev.reason, ev.level)
                            }
                            is AgentStreamEvent.Done -> Unit
                        }
                    },
                )
                updateUi(streamId) { it.copy(streaming = false) }
                val sources = (snapshot.today.map { it.sourceRef } +
                    snapshot.upcoming.map { it.sourceRef } +
                    snapshot.memoryHits.map { "memory:${it.uuid}" })
                    .filter { it.isNotBlank() }.distinct().take(8)
                db.chat().insertMessage(
                    ChatMessage(
                        threadId = threadId, role = "assistant",
                        text = result.answer.ifBlank { answerText.toString() },
                        toolRefs = result.steps.map { it.actionId }.distinct().joinToString(",", "[", "]") { "\"$it\"" },
                        sources = sources.joinToString(",", "[", "]") { "\"$it\"" },
                    ),
                )
                updateUi(streamId) { it.copy(text = result.answer.ifBlank { answerText.toString() }, sources = sources) }
                db.chat().touchThread(threadId, System.currentTimeMillis())
                val usage = BrainPrefs.usageSnapshot(context)
                _usage.value = usage
                refreshThreads()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _messages.value = _messages.value.map { if (it.streaming) it.copy(streaming = false, text = it.text.ifBlank { "Cancelled." }) else it }
                } else {
                    _error.value = (e.message ?: "Request failed").take(200)
                    _messages.value = _messages.value.filter { !it.streaming }
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun appendMessage(threadId: String, role: String, text: String) {
        val msg = ChatMessage(threadId = threadId, role = role, text = text)
        db.chat().insertMessage(msg)
        db.chat().touchThread(threadId, System.currentTimeMillis())
        appendUi(AskUiMessage(msg.id, role, text))
    }

    private fun appendUi(m: AskUiMessage) {
        _messages.value = _messages.value + m
    }

    private fun updateUi(id: String, change: (AskUiMessage) -> AskUiMessage) {
        _messages.value = _messages.value.map { if (it.id == id) change(it) else it }
    }

    private fun appendToolNote(toolName: String, ok: Boolean, message: String) {
        val note = (if (ok) "✓ " else "✗ ") + toolName + ": " + message.take(140)
        val last = _messages.value.lastOrNull()
        if (last != null && last.role == "assistant" && last.streaming) {
            updateUi(last.id) { it.copy(toolNote = ((it.toolNote?.plus("\n") ?: "") + note).take(600)) }
        }
    }

    private fun jobActive(): Boolean = job?.isActive == true

    companion object {
        fun parseSources(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(raw)
                List(arr.length()) { i -> arr.optString(i).takeIf { it.isNotBlank() } }.filterNotNull().take(12)
            }.getOrDefault(emptyList())
        }
    }
}
