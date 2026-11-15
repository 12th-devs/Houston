package com.astroboii47.commander.secondbrain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astroboii47.commander.SectionCard
import com.astroboii47.commander.memory.BrainMemory
import com.astroboii47.commander.memory.ConsolidationReport
import com.astroboii47.commander.secondbrain.db.BrainFact
import com.astroboii47.commander.secondbrain.db.FactKind
import com.astroboii47.commander.secondbrain.db.Verification
import com.astroboii47.commander.secondbrain.di.BrainGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val KINDS = listOf(FactKind.FACT, FactKind.GOAL, FactKind.EXPERIENCE, FactKind.PROJECT)

/**
 * Memory — searchable personal knowledge with verification states.
 * Facts the agent infers stay INFERRED until the user confirms them;
 * contradictions are flagged, never auto-resolved.
 */
@Composable
fun MemoryScreen(
    onOpenCaptures: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(FactKind.FACT) }
    var facts by remember { mutableStateOf(listOf<BrainFact>()) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BrainFact?>(null) }
    var report by remember { mutableStateOf<ConsolidationReport?>(null) }
    var review by remember { mutableStateOf(listOf<BrainFact>()) }
    var busy by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            val mem = BrainGraph.memory(context)
            facts = withContext(Dispatchers.IO) {
                if (query.isBlank()) mem.byKind(kind, 100)
                else mem.recall(query, 30).filter { kind == FactKind.FACT || it.kind == kind }
            }
            review = withContext(Dispatchers.IO) { mem.reviewCandidates(5) }
        }
    }
    LaunchedEffect(kind, query) { reload() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Memory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { showAdd = true }) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        query, { query = it },
        label = { Text("Search memory — facts, goals, experiences…") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KINDS.forEach { k ->
            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.replaceFirstChar { it.uppercase() }) })
        }
    }
    Spacer(Modifier.height(12.dp))

    if (facts.isEmpty()) {
        SectionCard(icon = Icons.AutoMirrored.Outlined.MenuBook, title = kind.replaceFirstChar { it.uppercase() }) {
            Text(
                if (query.isNotBlank()) "Nothing matches — try fewer words."
                else "Nothing here yet. Add your first ${kind} — the agent can suggest, but only you confirm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        facts.forEach { f ->
            MemoryRow(
                fact = f,
                onConfirm = {
                    scope.launch { withContext(Dispatchers.IO) { BrainGraph.memory(context).confirm(f.id) }; reload() }
                },
                onEdit = { editing = f },
                onDelete = {
                    scope.launch { withContext(Dispatchers.IO) { BrainGraph.memory(context).delete(f.id) }; reload() }
                },
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (review.isNotEmpty() && query.isBlank()) {
        Spacer(Modifier.height(12.dp))
        SectionCard(icon = Icons.Outlined.Check, title = "Ready for your review") {
            Text(
                "The agent inferred these with high confidence. Confirm the true ones; leave the rest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            review.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        f.text.take(140),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { BrainGraph.memory(context).confirm(f.id) }
                            reload()
                        }
                    }) { Text("Confirm") }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionCard(icon = Icons.Outlined.Psychology, title = "Memory health") {
        Text(
            "Deduplicates near-identical entries, expires temporary notes, flags contradictions for you to resolve.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        report = withContext(Dispatchers.IO) { BrainGraph.memory(context).consolidate() }
                        busy = false
                        reload()
                    }
                },
            ) { Text("Consolidate now") }
            OutlinedButton(onClick = onOpenCaptures) { Text("Captures") }
        }
        report?.let { r ->
            Spacer(Modifier.height(6.dp))
            Text(
                if (r.notes.isEmpty()) "Already tidy — no duplicates or expired notes."
                else r.notes.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showAdd) {
        MemoryEditDialog(
            initialKind = kind,
            initialText = "",
            onSave = { k, t ->
                scope.launch {
                    val mem = BrainGraph.memory(context)
                    val flags = withContext(Dispatchers.IO) {
                        mem.flagContradictions(t, mem.byKind(k, 100))
                    }
                    withContext(Dispatchers.IO) {
                        mem.remember(kind = k, text = t, sourceRef = "manual", sourceKind = "manual", verification = Verification.USER_CONFIRMED, confidence = 1.0)
                    }
                    showAdd = false
                    if (flags.isNotEmpty()) {
                        report = ConsolidationReport(0, 0, 0, flags.map { "Check: ${it.reason}" })
                    }
                    reload()
                }
            },
            onClose = { showAdd = false },
        )
    }
    editing?.let { f ->
        MemoryEditDialog(
            initialKind = f.kind,
            initialText = f.text,
            onSave = { _, t ->
                scope.launch {
                    withContext(Dispatchers.IO) { BrainGraph.memory(context).edit(f.id, t) }
                    editing = null
                    reload()
                }
            },
            onClose = { editing = null },
        )
    }
}

@Composable
private fun MemoryRow(
    fact: BrainFact,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(
        icon = Icons.AutoMirrored.Outlined.MenuBook,
        title = verificationLabel(fact.verification),
    ) {
        Text(fact.text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "${fact.kind} · confidence ${(fact.confidence * 100).toInt()}% · source: ${fact.sourceRef.take(40)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (fact.verification != Verification.USER_CONFIRMED) {
                TextButton(onClick = onConfirm) {
                    Icon(Icons.Outlined.Check, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Confirm")
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit memory") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete memory", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun verificationLabel(v: String): String = when (v) {
    Verification.USER_CONFIRMED -> "Confirmed by you"
    Verification.IMPORTED -> "Imported"
    Verification.INFERRED -> "AI inference — unconfirmed"
    Verification.TEMPORARY -> "Temporary"
    Verification.SUPERSEDED -> "Superseded"
    else -> v
}

@Composable
private fun MemoryEditDialog(
    initialKind: String,
    initialText: String,
    onSave: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    var kind by remember { mutableStateOf(initialKind) }
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initialText.isEmpty()) "Add memory" else "Edit memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KINDS.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.take(4)) })
                    }
                }
                OutlinedTextField(text, { text = it }, label = { Text("What should be remembered?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text(
                    "Manual entries are confirmed by you. Agent suggestions always arrive unconfirmed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.trim().length >= 3, onClick = { onSave(kind, text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
