package com.astroboii47.commander.secondbrain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astroboii47.commander.agent.AgentLevel
import com.astroboii47.commander.secondbrain.di.BrainPrefs

/**
 * Ask — grounded conversation. Streams answers, keeps history, runs tools
 * with permission gating, shows sources and step outcomes, supports cancel.
 */
@Composable
fun AskScreen(
    vm: AskViewModel = viewModel(),
    prefill: String = "",
    prefillNonce: Int = 0,
    onPrefillConsumed: () -> Unit = {},
) {
    val threads by vm.threads.collectAsState()
    val threadId by vm.threadId.collectAsState()
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val pending by vm.pending.collectAsState()
    val usage by vm.usage.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var lastSentNonce by remember { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    // Home input and voice handoffs arrive as prefill: send once when idle.
    // (Previously the prefill was consumed without sending — user input vanished.)
    LaunchedEffect(prefillNonce, busy) {
        if (prefill.isNotBlank() && prefillNonce != lastSentNonce && !busy) {
            lastSentNonce = prefillNonce
            onPrefillConsumed()
            vm.send(prefill)
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Ask", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, "Conversation history") }
        IconButton(onClick = { vm.newThread() }) { Icon(Icons.Outlined.Add, "New conversation") }
    }
    Spacer(Modifier.height(8.dp))

    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    "Ask about your week, your swimming, your workload — or tell me to remember something. " +
                        "Answers use your verified data; guesses are labeled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "What do I need to do this week?",
                        "How has my swimming improved?",
                        "Find time for studying tomorrow.",
                        "What changed since yesterday?",
                    ).forEach { suggestion ->
                        FilterChip(
                            selected = false,
                            onClick = { vm.send(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
        }
        items(messages, key = { it.id }) { m ->
            AskBubble(message = m)
        }
        if (busy) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    pending.forEach { p ->
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    (if (p.level == AgentLevel.CONFIRM) "Confirmation needed" else "Approval needed") + ": ${p.reason}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { vm.dismissApproval(p.key) }) { Text("Deny") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.approve(p.key) }) { Text("Approve") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            OutlinedButton(onClick = { vm.cancel() }, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.Outlined.Close, null)
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }
        androidx.compose.runtime.key(prefillNonce) {
            BrainBar(
                hint = "Ask, capture, command…",
                initial = prefill,
                enabled = !busy,
                onSubmit = { vm.send(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    usage?.let { (calls, inChars, outChars) ->
        Text(
            "30-day usage: $calls calls · ~${inChars / 1000}k in / ${outChars / 1000}k out chars · est $${"%.2f".format(BrainPrefs.estimateUsd(inChars, outChars))}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("Conversations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (threads.isEmpty()) Text("No conversations yet.")
                    threads.take(20).forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { vm.openThread(t.id); showHistory = false },
                                modifier = Modifier.weight(1f),
                            ) { Text(t.title.take(40), maxLines = 1) }
                            if (t.id == threadId) Text("•", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
        )
    }
    } // root column
}

@Composable
private fun AskBubble(message: AskUiMessage) {
    val mine = message.role == "user"
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(message.text.ifBlank { "…" }, style = MaterialTheme.typography.bodyMedium)
            message.toolNote?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            message.sources.takeIf { it.isNotEmpty() }?.let { refs ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sources: " + refs.take(4).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (message.streaming) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
