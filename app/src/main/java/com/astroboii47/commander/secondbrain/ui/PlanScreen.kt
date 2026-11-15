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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.astroboii47.commander.ICalStore
import com.astroboii47.commander.PowerSchoolStore
import com.astroboii47.commander.SectionCard
import com.astroboii47.commander.plan.DayBlock
import com.astroboii47.commander.plan.PlannedAssignment
import com.astroboii47.commander.plan.Planner
import com.astroboii47.commander.plan.StudySuggestion
import com.astroboii47.commander.psIsoToLocalDate
import com.astroboii47.commander.secondbrain.context.BrainSnapshot
import com.astroboii47.commander.secondbrain.db.PlanBlock
import com.astroboii47.commander.secondbrain.di.BrainGraph
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plan — Today timeline / Upcoming groups / Calendar. Commitments and
 * AI suggestions are visually distinct; suggestions never persist without
 * an explicit tap (approval gate).
 */
@Composable
fun PlanScreen(
    snapshot: BrainSnapshot?,
    onOpenCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(0) } // 0 today, 1 upcoming, 2 calendar
    var blocks by remember { mutableStateOf(listOf<PlanBlock>()) }
    var suggestions by remember { mutableStateOf(listOf<StudySuggestion>()) }
    var generating by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    fun reloadBlocks() {
        scope.launch {
            blocks = withContext(Dispatchers.IO) {
                BrainGraph.db(context).plans().forRange(today.toString(), today.plusDays(7).toString())
            }
        }
    }
    LaunchedEffect(Unit) { reloadBlocks() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Today", "Upcoming", "Calendar").forEachIndexed { i, label ->
            FilterChip(selected = view == i, onClick = { view = i }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(12.dp))

    when (view) {
        0 -> {
            // Timeline: commitments first, accepted suggestions distinctly styled.
            val todays = blocks.filter { it.dateIso == today.toString() }
            if ((snapshot?.today.isNullOrEmpty()) && todays.isEmpty()) {
                SectionCard(icon = Icons.Outlined.Schedule, title = "Today") {
                    Text(
                        "Nothing scheduled — an open day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SectionCard(icon = Icons.Outlined.Schedule, title = "Today") {
                    snapshot?.today?.forEach { item ->
                        TimelineRow(
                            title = item.title,
                            meta = item.detail,
                            tag = item.domain,
                            suggestion = false,
                        )
                    }
                    todays.forEach { b ->
                        TimelineRow(
                            title = b.title + if (b.status == "done") " ✓" else "",
                            meta = "${fmtMin(b.startMin)}–${fmtMin(b.endMin)} · ${b.rationale.take(90)}",
                            tag = "suggestion",
                            suggestion = true,
                            onDone = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { BrainGraph.db(context).plans().setStatus(b.id, "done") }
                                    reloadBlocks()
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { BrainGraph.db(context).plans().delete(b.id) }
                                    reloadBlocks()
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(icon = Icons.Outlined.AutoAwesome, title = "Study suggestions") {
                if (generating) {
                    CircularProgressIndicator()
                } else if (suggestions.isEmpty()) {
                    Text(
                        "I can place study blocks for your upcoming work into open time. Nothing is scheduled until you approve it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        generating = true
                        scope.launch {
                            val made = withContext(Dispatchers.IO) { buildSuggestions(context) }
                            suggestions = made
                            generating = false
                        }
                    }) { Text("Generate study plan") }
                } else {
                suggestions.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (s.startMin < 0) "Needs a slot: ${s.minutes} min" else "${fmtMin(s.startMin)} · ${s.minutes} min on ${s.dateIso}") +
                                    (if (s.assignmentName.isNotBlank()) " — ${s.assignmentName}" else ""),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(s.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.startMin >= 0) {
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        BrainGraph.db(context).plans().upsert(
                                            PlanBlock(
                                                dateIso = s.dateIso, startMin = s.startMin,
                                                endMin = s.startMin + s.minutes,
                                                kind = "suggestion",
                                                title = if (s.assignmentName.isNotBlank()) "Study: ${s.assignmentName}" else "Study block",
                                                refId = s.assignmentId,
                                                source = "agent", status = "accepted",
                                                rationale = s.rationale,
                                            ),
                                        )
                                    }
                                    suggestions = suggestions - s
                                    reloadBlocks()
                                }
                            }) { Text("Add") }
                                IconButton(onClick = { suggestions = suggestions - s }) {
                                    Icon(Icons.Outlined.Close, "Dismiss suggestion")
                                }
                            }
                        }
                    }
                }
            }
        }
        1 -> {
            // Upcoming groups: Today / Tomorrow / This week / Later.
            val groups = remember(snapshot) {
                val t = LocalDate.now()
                val items = (snapshot?.upcoming.orEmpty())
                mapOf(
                    "Today" to items.filter { it.dateIso == t.toString() },
                    "Tomorrow" to items.filter { it.dateIso == t.plusDays(1).toString() },
                    "This week" to items.filter {
                        val d = it.dateIso ?: return@filter false
                        d > t.plusDays(1).toString() && d <= t.plusDays(7).toString()
                    },
                    "Later" to items.filter {
                        val d = it.dateIso ?: return@filter true
                        d > t.plusDays(7).toString()
                    },
                ).filterValues { it.isNotEmpty() }
            }
            if (groups.isEmpty()) {
                SectionCard(icon = Icons.Outlined.Schedule, title = "Upcoming") {
                    Text("Nothing upcoming in your connected data.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                groups.forEach { (label, items) ->
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    SectionCard(icon = Icons.Outlined.Schedule, title = "$label (${items.size})") {
                        items.forEach { item ->
                            TimelineRow(title = item.title, meta = item.detail, tag = item.domain, suggestion = false)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        else -> {
            TextButton(onClick = onOpenCalendar) { Text("Open full calendar") }
        }
    }
}

@Composable
private fun TimelineRow(
    title: String,
    meta: String,
    tag: String,
    suggestion: Boolean,
    onDone: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (suggestion) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (suggestion) "AI suggestion · needs your approval to change" else tag,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (suggestion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDone != null) {
                IconButton(onClick = onDone) { Icon(Icons.Outlined.Check, "Mark done") }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove") }
            }
        }
    }
}

private fun fmtMin(min: Int): String {
    if (min < 0) return "—"
    return "%d:%02d".format(min / 60, min % 60)
}

private suspend fun buildSuggestions(context: android.content.Context): List<StudySuggestion> {
    val courses = PowerSchoolStore.loadCourses(context)
    val today = LocalDate.now()
    val byDate = HashMap<String, List<DayBlock>>()
    for (d in 0..7) {
        val day = today.plusDays(d.toLong())
        val occ = runCatching { ICalStore.occurrences(context, day, day) }.getOrDefault(emptyList())
        byDate[day.toString()] = occ.mapNotNull { o ->
            val st = o.startTime ?: return@mapNotNull null
            val en = o.endTime ?: st.plusHours(1)
            DayBlock(st.hour * 60 + st.minute, en.hour * 60 + en.minute, o.summary, "commitment", o.feedName)
        }
    }
    val acceptedIds = BrainGraph.db(context).plans()
        .forRange(today.toString(), today.plusDays(14).toString())
        .filter { it.status == "accepted" || it.status == "done" }
        .map { it.refId }.filter { it.isNotBlank() }.toSet()
    val assignments = PowerSchoolStore.upcoming(courses, today, 14).map {
        PlannedAssignment(
            id = it.id.ifBlank { it.name ?: "" }, name = it.name ?: "Assignment",
            course = it.courseName, dueIso = it.dueIso, estMin = null, status = it.status,
        )
    }
    return Planner.recommendStudy(assignments, byDate, today, acceptedIds = acceptedIds).take(8)
}
