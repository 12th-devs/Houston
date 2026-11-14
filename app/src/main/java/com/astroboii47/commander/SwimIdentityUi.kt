package com.astroboii47.commander

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Swimmer identity linking: search Meet Mobile for a swimmer, attach any
 * number of verified identities to the same local swimmer, browse each
 * identity's cross-meet history, and jump straight into guided import.
 * Linking never auto-imports — it only widens identity matching.
 */
@Composable
fun SwimmerLinkDialog(
    profile: SwimmerProfile,
    onSaveLinks: (List<LinkedSwimmer>) -> Unit,
    onImportMeet: (MmMeet) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(listOf<MmSwimmer>()) }
    var historyFor by remember { mutableStateOf<LinkedSwimmer?>(null) }
    var history by remember { mutableStateOf(listOf<MmMeet>()) }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = mmUserMessage(e)
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (historyFor == null) "Link Meet Mobile swimmer" else "Meets · ${historyFor!!.label.take(30)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (historyFor == null) {
                    OutlinedTextField(query, { query = it }, label = { Text("Swimmer name (3+ characters)") }, modifier = Modifier.fillMaxWidth())
                    results.take(10).forEach { s ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "id ${s.uniqueId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val linked = profile.links.any { it.uniqueId == s.uniqueId }
                            TextButton(
                                enabled = !linked,
                                onClick = {
                                    onSaveLinks(
                                        profile.links + LinkedSwimmer(s.uniqueId, s.label),
                                    )
                                },
                            ) { Text(if (linked) "Linked" else "Link") }
                        }
                    }
                    if (profile.links.isNotEmpty()) {
                        Text(
                            "Linked identities (${profile.links.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    profile.links.forEach { l ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.label, style = MaterialTheme.typography.bodyMedium)
                                if (l.meetName.isNotBlank()) {
                                    Text(
                                        "via ${l.meetName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = {
                                historyFor = l
                                history = emptyList()
                                run {
                                    history = mmSwimmerHistory(l.uniqueId)
                                    if (history.isEmpty()) error = "No meet history published for this identity."
                                }
                            }) { Text("Meets") }
                            TextButton(onClick = { onSaveLinks(profile.links.filter { it.uniqueId != l.uniqueId }) }) {
                                Text("Unlink")
                            }
                        }
                    }
                    Text(
                        "Linking only widens name matching for imports. Confirm identity on screen before importing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (history.isEmpty() && !busy && error == null) {
                        Text("Loading meet history…", style = MaterialTheme.typography.bodySmall)
                    }
                    history.take(20).forEach { m ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    listOfNotNull(m.city.takeIf { it.isNotBlank() }, m.dateIso).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onImportMeet(m) }) { Text("Import") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (historyFor == null) {
                TextButton(enabled = !busy && query.trim().length >= 3, onClick = {
                    run {
                        results = parseMmSwimmers(mmSearchSwimmers(query.trim()))
                        if (results.isEmpty()) error = "No swimmers matched. Check the spelling."
                    }
                }) { Text("Search") }
            } else {
                TextButton(onClick = { historyFor = null; error = null }) { Text("Back") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
