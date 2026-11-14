package com.astroboii47.commander

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SchoolAlertsCard(prefs: SchoolAlertPrefs, onSave: (SchoolAlertPrefs) -> Unit) {
    var drop by remember(prefs) { mutableStateOf(prefs.dropThreshold.toString()) }
    var below by remember(prefs) { mutableStateOf(prefs.gradeBelowThreshold?.toString() ?: "") }
    var days by remember(prefs) { mutableStateOf(prefs.upcomingDays.toString()) }
    SectionCard(icon = Icons.Outlined.Notifications, title = "School alerts") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Missing work", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = prefs.notifyMissing, onCheckedChange = { onSave(prefs.copy(notifyMissing = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("New assignments", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = prefs.notifyNewAssignment, onCheckedChange = { onSave(prefs.copy(notifyNewAssignment = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Upcoming deadlines", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = prefs.notifyUpcoming, onCheckedChange = { onSave(prefs.copy(notifyUpcoming = it)) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Improvements", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = prefs.notifyImproved, onCheckedChange = { onSave(prefs.copy(notifyImproved = it)) })
        }
        OutlinedTextField(
            drop, { drop = it; it.toDoubleOrNull()?.let { d -> onSave(prefs.copy(dropThreshold = d.coerceIn(0.5, 20.0))) } },
            label = { Text("Grade drop alert (pts)") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        OutlinedTextField(
            below, {
                below = it
                onSave(prefs.copy(gradeBelowThreshold = it.toDoubleOrNull()))
            },
            label = { Text("Alert below % (empty = off)") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        OutlinedTextField(
            days, { days = it; it.toLongOrNull()?.let { d -> onSave(prefs.copy(upcomingDays = d.coerceIn(1, 14))) } },
            label = { Text("Upcoming window (days)") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
fun BackgroundCard(
    briefingOn: Boolean,
    briefingTime: String,
    syncOn: Boolean,
    syncHours: Long,
    lastSync: String?,
    onToggleBriefing: (Boolean) -> Unit,
    onToggleSync: (Boolean) -> Unit,
    onInterval: (Long) -> Unit,
    onSyncNow: () -> Unit,
) {
    SectionCard(icon = Icons.Outlined.Sync, title = "Background work") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Morning briefing", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Daily at $briefingTime · best-effort, may defer in battery saver",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = briefingOn, onCheckedChange = onToggleBriefing)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Background sync", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Calendars + grades every ${syncHours}h · retries with backoff",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = syncOn, onCheckedChange = onToggleSync)
        }
        if (syncOn) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2L, 6L, 12L, 24L).forEach { h ->
                    FilterChip(selected = syncHours == h, onClick = { onInterval(h) }, label = { Text("${h}h") })
                }
            }
        }
        lastSync?.let {
            Text(
                "Last background sync ${it.take(16).replace('T', ' ')}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Icon(Icons.Outlined.Sync, null)
            Spacer(Modifier.width(8.dp))
            Text("Sync now")
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun AutomationsCard(
    rules: List<AutomationRule>,
    runs: List<AutomationRun>,
    notifyPrefs: Map<String, Boolean>,
    onToggleRule: (String, Boolean) -> Unit,
    onToggleChannel: (String, Boolean) -> Unit,
) {
    SectionCard(icon = Icons.Outlined.AutoMode, title = "Automations (${rules.count { it.enabled }} on)") {
        rules.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${r.trigger} → ${r.action}${r.timeIso?.let { " @ $it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = r.enabled, onCheckedChange = { onToggleRule(r.id, it) })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Notification channels", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        notifyPrefs.forEach { (ch, on) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(ch.removePrefix("houston_"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = on, onCheckedChange = { onToggleChannel(ch, it) })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard(icon = Icons.Outlined.History, title = "Recent runs (${runs.size})") {
        if (runs.isEmpty()) {
            Text(
                "No automation runs yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        runs.take(8).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (r.success) "✓" else "✗",
                    color = if (r.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.width(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(r.ruleId, style = MaterialTheme.typography.bodySmall)
                    Text(r.detail.take(100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "Background work is best-effort; exact timing is not guaranteed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
