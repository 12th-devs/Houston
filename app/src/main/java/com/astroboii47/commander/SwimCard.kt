package com.astroboii47.commander

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun SwimCard(
    state: SwimState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSaveTargets: (event: String, start: Double?, best: Double?, goal: Double?) -> Unit,
    onAddEntry: (date: String, title: String, detail: String?) -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    var showTargets by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val progress = swimProgressPct(state.baselineSec, state.bestSec, state.goalSec)
    SectionCard(icon = Icons.Outlined.Waves, title = "Swimming", viewAllLabel = "View All", onViewAll = onToggleExpanded) {
        Text(state.eventName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Current Best", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.bestSec?.let { formatSwimTime(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.goalSec?.let { formatSwimTime(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
            }
            if (state.bestSec != null && state.goalSec != null && state.bestSec > state.goalSec) {
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingDown, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("%.2fs".format(state.bestSec - state.goalSec), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("To Go", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (progress ?: 0f).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                progress?.let { "${(it * 100).toInt()}%" } ?: "—",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        val entries = if (expanded) state.log else state.log.take(2)
        for (e in entries) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (e.title.contains("improv", true)) Icons.Outlined.ShowChart else Icons.Outlined.Group,
                        null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    e.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { relDayLabel(it) } ?: e.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { onDeleteEntry(e.id) }) {
                    Icon(Icons.Outlined.Delete, "Delete entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (state.log.isEmpty()) {
            Text("No entries yet — log a practice to start the history.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showLog = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log practice")
            }
            OutlinedButton(onClick = { showTargets = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Targets")
            }
        }
    }

    if (showTargets) {
        var eventName by remember(state.eventName) { mutableStateOf(state.eventName) }
        var start by remember(state.baselineSec) { mutableStateOf(state.baselineSec?.let { formatSwimTime(it) } ?: "") }
        var best by remember(state.bestSec) { mutableStateOf(state.bestSec?.let { formatSwimTime(it) } ?: "") }
        var goal by remember(state.goalSec) { mutableStateOf(state.goalSec?.let { formatSwimTime(it) } ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showTargets = false },
            title = { Text("Swim targets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(eventName, { eventName = it }, label = { Text("Event (e.g. 100 Breast – SCY)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it; error = null }, label = { Text("Season start (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(best, { best = it; error = null }, label = { Text("Current best (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(goal, { goal = it; error = null }, label = { Text("Goal (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = if (start.isBlank()) null else parseSwimTime(start)
                    val b = if (best.isBlank()) null else parseSwimTime(best)
                    val g = if (goal.isBlank()) null else parseSwimTime(goal)
                    if ((start.isNotBlank() && s == null) || (best.isNotBlank() && b == null) || (goal.isNotBlank() && g == null)) {
                        error = "Times look like 1:08.20 or 68.20."
                    } else {
                        onSaveTargets(eventName.ifBlank { "Swim" }, s, b, g)
                        showTargets = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTargets = false }) { Text("Cancel") } },
        )
    }

    if (showLog) {
        var title by remember { mutableStateOf("") }
        var detail by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Log practice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it; error = null }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(detail, { detail = it }, label = { Text("Detail (e.g. 6,800 yd · Endurance)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it; error = null }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) {
                        error = "Give the entry a title."
                    } else if (psIsoToLocalDate(date) == null) {
                        error = "Date must look like 2026-11-14."
                    } else {
                        onAddEntry(date, title, detail.ifBlank { null })
                        showLog = false
                    }
                }) { Text("Log") }
            },
            dismissButton = { TextButton(onClick = { showLog = false }) { Text("Cancel") } },
        )
    }
}
