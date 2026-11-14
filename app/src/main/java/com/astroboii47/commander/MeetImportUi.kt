package com.astroboii47.commander

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided Meet Mobile import: search meet → load program → pick event/round →
 * confirm swimmer identity → import verified results. Every failure is an
 * honest unavailable state; manual entry always remains available.
 */
@Composable
fun MeetImportDialog(
    profileName: String,
    identityLabels: List<String> = listOf(profileName),
    initialMeet: MmMeet? = null,
    onDismiss: () -> Unit,
    onImport: (List<RaceResult>, String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { androidx.compose.runtime.mutableIntStateOf(if (initialMeet == null) 0 else 1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var keyword by remember { mutableStateOf("") }
    var meets by remember { mutableStateOf(listOf<MmMeet>()) }
    var meet by remember { mutableStateOf(initialMeet) }
    var events by remember { mutableStateOf(listOf<MmEventNode>()) }
    var event by remember { mutableStateOf<MmEventNode?>(null) }
    var entries by remember { mutableStateOf(listOf<MmEntry>()) }
    var confirmed by remember { mutableStateOf(false) }

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

    fun loadProgram(m: MmMeet) {
        meet = m
        run {
            val payload = mmGet("meet", mapOf("id" to m.id.toLong()))
            val evs = parseMmEvents(payload)
            withContext(Dispatchers.Main) {
                events = evs
                if (evs.isEmpty()) {
                    error = "Meet loaded, but no parsable program was published for it."
                } else step = 1
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialMeet) {
        if (initialMeet != null && events.isEmpty()) loadProgram(initialMeet)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(when (step) { 0 -> "Find meet"; 1 -> "Pick event"; 2 -> "Confirm & import"; else -> "Import" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                when (step) {
                    0 -> {
                        OutlinedTextField(keyword, { keyword = it }, label = { Text("Meet name (e.g. NCAA)") }, modifier = Modifier.fillMaxWidth())
                        meets.take(8).forEach { m ->
                            Text(
                                "${m.name}${m.city.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    loadProgram(m)
                                }.padding(vertical = 4.dp),
                            )
                        }
                        if (meets.isEmpty()) {
                            Text(
                                "Search is live and bounded (page 1, 20 max). Unpublished meets simply return nothing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    1 -> {
                        Text(meet?.name ?: "", style = MaterialTheme.typography.bodyMedium)
                        events.take(30).forEach { e ->
                            Text(
                                e.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    event = e
                                    run {
                                        val payload = mmGet("round", mapOf("ids" to e.id.toLong()))
                                        val parsed = parseMmRound(payload, e.name)
                                        withContext(Dispatchers.Main) {
                                            entries = parsed
                                            if (parsed.isEmpty()) {
                                                error = "Round loaded, but no finished entries were published for it (pending rounds carry seed times only)."
                                            } else step = 2
                                        }
                                    }
                                }.padding(vertical = 4.dp),
                            )
                        }
                    }
                    2 -> {
                        val known = identityLabels.map { it.trim() }.filter { it.length >= 2 }
                        val mine = entries.filter { en ->
                            known.any { k -> en.swimmerLabel.contains(k, ignoreCase = true) }
                        }
                        Text(
                            "${entries.size} finished entr${if (entries.size == 1) "y" else "ies"} in ${event?.name}. " +
                                if (known.isEmpty()) "Set your name in the swimmer profile to auto-match."
                                else if (mine.isEmpty()) "No entry matches your ${if (known.size == 1) "name" else "linked identities"} — import only after confirming identity."
                                else "${mine.size} match your ${if (known.size == 1) "name" else "linked identities"}.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        (if (mine.isNotEmpty()) mine else entries).take(6).forEach { en ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${en.swimmerLabel} · ${en.timeSec?.let { formatSwimTime(it) } ?: "—"}${en.place?.let { " · #$it" } ?: ""}${if (en.dq) " · DQ" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                            Text(
                                "I confirm these results are mine (or my swimmer's).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> TextButton(enabled = !busy && keyword.trim().length >= 2, onClick = {
                    run {
                        val payload = withContext(Dispatchers.IO) {
                            // POST search via GET-compatible path is not allowed; use mmGet only for GET routes.
                            // Meet search is POST — performed here with URLConnection POST.
                            mmPostSearch(keyword.trim())
                        }
                        withContext(Dispatchers.Main) {
                            meets = parseMmMeets(payload)
                            if (meets.isEmpty()) error = "No meets matched. Try a different name, city, or date."
                        }
                    }
                }) { Text("Search") }
                2 -> TextButton(enabled = !busy && confirmed && entries.isNotEmpty(), onClick = {
                    val m = meet ?: return@TextButton
                    val ev = event ?: return@TextButton
                    val known = identityLabels.map { it.trim() }.filter { it.length >= 2 }
                    val mine = entries.filter { en ->
                        known.any { k -> en.swimmerLabel.contains(k, ignoreCase = true) }
                    }.ifEmpty { entries.filter { !it.dq && it.timeSec != null }.take(1) }
                    val results = mine.mapNotNull { en ->
                        val t = en.timeSec ?: return@mapNotNull null
                        RaceResult(
                            id = resultDedupeKey(m.id, ev.id, SwimCourse.SCY, t, en.swimmerLabel),
                            meetId = m.id, meetName = m.name, eventId = ev.id, eventName = ev.name,
                            course = SwimCourse.SCY, timeSec = t, place = en.place,
                            dateIso = LocalDate.now().toString(), swimmerRef = en.swimmerLabel,
                            splits = en.splits, dq = en.dq, provenance = "meetmobile:${m.id}",
                        )
                    }
                    if (results.isEmpty()) {
                        onStatus("No importable finish times in this round.")
                        onDismiss()
                    } else onImport(results, m.name)
                }) { Text("Import") }
                else -> TextButton(onClick = {}) { Text("…") }
            }
        },
        dismissButton = {
            Row {
                if (step > 0) TextButton(onClick = { step -= 1; error = null }) { Text("Back") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private suspend fun mmPostSearch(keyword: String): JVal = mmPost(
    "search",
    mapOf("keyword" to keyword.trim(), "currentPage" to 1, "pageSize" to 20),
) {
    "{\"keyword\":\"${keyword.trim().replace("\"", "")}\",\"currentPage\":1,\"pageSize\":20}"
}
