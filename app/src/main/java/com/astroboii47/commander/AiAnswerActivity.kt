package com.astroboii47.commander

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presents AI answers in a focused conversation rather than a transient toast. */
class AiAnswerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiConversation(intent.getStringExtra("prompt").orEmpty()) }
    }
    @Composable private fun AiConversation(initialPrompt: String) {
        val provider = AiSettings.provider(this)
        val model = AiSettings.model(this, provider)
        var messages by remember { mutableStateOf<List<AskMessage>>(emptyList()) }
        var draft by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        fun send(prompt: String) {
            if (prompt.isBlank() || busy) return
            val history = messages + AskMessage("user", prompt.trim())
            messages = history; draft = ""; busy = true; error = null
        }
        // The actual request path uses the IO dispatcher and preserves conversation history.
        LaunchedEffect(messages, busy) {
            if (busy && messages.lastOrNull()?.role == "user") {
                val history = messages
                val response = withContext(Dispatchers.IO) { AiApi.generate(this@AiAnswerActivity, history) }
                response.fold(
                    onSuccess = { messages = history + it.attachments + AskMessage("assistant", it.text) },
                    onFailure = { error = it.message ?: "AI request failed" },
                )
                busy = false
            }
        }
        LaunchedEffect(initialPrompt) {
            if (initialPrompt.isNotBlank() && messages.isEmpty()) {
                messages = listOf(AskMessage("user", initialPrompt.trim()))
                busy = true
            }
        }
        MaterialTheme {
            Scaffold(topBar = {
                Row(Modifier.fillMaxWidth().padding(18.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Ask ${provider.label}", style = MaterialTheme.typography.titleLarge)
                        Text(model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { startActivity(Intent(this@AiAnswerActivity, SettingsActivity::class.java).putExtra("focus", "AI provider")) }) { Text("Settings") }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                    LazyColumn(Modifier.weight(1f)) {
                        items(messages) { message ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (message.role == "user") "You" else provider.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(10.dp)) } }
                    }
                    Row(Modifier.fillMaxWidth().imePadding(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Ask a follow-up") }, maxLines = 3)
                        Button(onClick = { send(draft) }, enabled = draft.isNotBlank() && !busy, modifier = Modifier.padding(start = 8.dp)) { Text("Send") }
                    }
                }
            }
        }
    }
}
