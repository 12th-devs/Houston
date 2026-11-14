package com.astroboii47.commander

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a")
private val DAY_HEADER_FMT = DateTimeFormatter.ofPattern("EEEE, MMM d")

/** Google-Calendar-style page: month grid on top, scrolling agenda beneath. */
@Composable
fun CalendarPage(
    feeds: List<ICalFeed>,
    feedsVersion: Int,
    psCourses: List<PsCourse>,
    initialDay: LocalDate,
    onFeedsChanged: () -> Unit,
    onSyncFeed: (ICalFeed) -> Unit,
    onSyncFeeds: () -> Unit,
    syncingFeeds: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var month by remember(initialDay) { mutableStateOf(YearMonth.from(initialDay)) }
    var selectedDay by remember(initialDay) { mutableStateOf(initialDay) }
    var showAddFeed by remember { mutableStateOf(false) }
    val palette = rememberFeedPalette()
    val schoolDot = MaterialTheme.colorScheme.secondary

    val monthStart = remember(month) { month.atDay(1) }
    val monthEnd = remember(month) { month.atEndOfMonth() }
    val agendaEnd = remember(monthEnd) { monthEnd.plusDays(45) }
    val occurrences = remember(feedsVersion, feeds, monthStart, agendaEnd) {
        runCatching { ICalStore.occurrences(context, monthStart.minusWeeks(1), agendaEnd) }.getOrDefault(emptyList())
    }
    val dots = remember(occurrences, psCourses) {
        val map = mutableMapOf<LocalDate, MutableList<androidx.compose.ui.graphics.Color>>()
        for (o in occurrences) {
            val idx = feeds.indexOfFirst { it.id == o.feedId }.takeIf { it >= 0 } ?: 0
            map.getOrPut(o.date) { mutableListOf() }.add(palette[idx % palette.size])
        }
        for (a in PowerSchoolStore.distinctAssignments(psCourses)) {
            val d = a.dueIso?.let { psIsoToLocalDate(it) } ?: continue
            map.getOrPut(d) { mutableListOf() }.add(schoolDot)
        }
        map
    }
    fun feedColor(id: String): androidx.compose.ui.graphics.Color {
        val idx = feeds.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0
        return palette[idx % palette.size]
    }

    MonthGridCard(
        month = month,
        selectedDay = selectedDay,
        today = today,
        dots = dots,
        legend = remember(feeds) {
            feeds.mapIndexed { i, f -> f.name to palette[i % palette.size] } + listOf("School" to schoolDot)
        },
        onPrev = { month = month.minusMonths(1) },
        onNext = { month = month.plusMonths(1) },
        onToday = { month = YearMonth.now(); selectedDay = LocalDate.now() },
        onSelect = { selectedDay = it },
    )
    Spacer(Modifier.height(12.dp))

    // Selected day, then upcoming grouped by day.
    val selectedItems = remember(selectedDay, occurrences, psCourses) {
        val ical = occurrences.filter { it.date == selectedDay }
        val ps = PowerSchoolStore.dueOn(psCourses, selectedDay)
        Pair(ical, ps)
    }
    val upcomingDays = remember(occurrences, psCourses, selectedDay) {
        val days = sortedSetOf<LocalDate>()
        for (o in occurrences) if (!o.date.isBefore(selectedDay.plusDays(1)) && !o.date.isAfter(selectedDay.plusDays(30))) days.add(o.date)
        for (a in PowerSchoolStore.distinctAssignments(psCourses)) {
            a.dueIso?.let { psIsoToLocalDate(it) }?.let { d ->
                if (!d.isBefore(selectedDay.plusDays(1)) && !d.isAfter(selectedDay.plusDays(30))) days.add(d)
            }
        }
        days.toList()
    }

    SectionCard(icon = Icons.Outlined.CalendarMonth, title = selectedDay.format(DAY_HEADER_FMT)) {
        if (selectedItems.first.isEmpty() && selectedItems.second.isEmpty()) {
            Text(
                "Nothing scheduled — enjoy the open day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        selectedItems.first.forEach { o ->
            AgendaEventRow(
                color = feedColor(o.feedId),
                title = o.summary,
                meta = listOfNotNull(
                    if (o.allDay) "All day"
                    else o.startTime?.let { s ->
                        val e = o.endTime
                        if (e != null && e != s) "${s.format(TIME_FMT)} – ${e.format(TIME_FMT)}"
                        else s.format(TIME_FMT)
                    },
                    o.location?.takeIf { it.isNotBlank() },
                    o.feedName,
                ).joinToString(" · "),
            )
        }
        selectedItems.second.forEach { a ->
            AgendaEventRow(
                color = if (a.status == "missing") MaterialTheme.colorScheme.error else schoolDot,
                title = (a.name ?: "Assignment") + " (${a.courseName})",
                meta = buildString {
                    a.category?.let { append(it).append(" · ") }
                    val s = psScoreText(a)
                    if (s.isNotEmpty()) append(s) else append(a.status)
                },
            )
        }
    }
    if (upcomingDays.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SectionCard(icon = Icons.Outlined.Event, title = "Upcoming") {
            upcomingDays.forEach { day ->
                Text(
                    relDayLabel(day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                for (o in occurrences.filter { it.date == day }) {
                    AgendaEventRow(
                        color = feedColor(o.feedId),
                        title = o.summary,
                        meta = listOfNotNull(
                            if (o.allDay) "All day"
                            else o.startTime?.let { s ->
                                val e = o.endTime
                                if (e != null && e != s) "${s.format(TIME_FMT)} – ${e.format(TIME_FMT)}"
                                else s.format(TIME_FMT)
                            },
                            o.feedName,
                        ).joinToString(" · "),
                    )
                }
                for (a in PowerSchoolStore.distinctAssignments(psCourses)
                    .filter { it.dueIso?.let { d -> psIsoToLocalDate(d) == day } == true }) {
                    AgendaEventRow(
                        color = if (a.status == "missing") MaterialTheme.colorScheme.error else schoolDot,
                        title = (a.name ?: "Assignment") + " (${a.courseName})",
                        meta = buildString {
                            a.category?.let { append(it).append(" · ") }
                            val s = psScoreText(a)
                            if (s.isNotEmpty()) append(s) else append(a.status)
                        },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    SectionHeader(Icons.Outlined.CalendarMonth, "Calendars")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            if (feeds.isEmpty()) {
                Text(
                    "No calendars yet. Add an iCal link — e.g. a Google Calendar “Secret address in iCal format”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            feeds.forEachIndexed { i, feed ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(palette[i % palette.size]))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(feed.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            feed.lastError ?: feed.url.take(64),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (feed.lastError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        feed.lastSync?.let {
                            Text("Synced ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (syncingFeeds) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { onSyncFeed(feed) }, enabled = !syncingFeeds) {
                            Icon(Icons.Outlined.Sync, "Sync ${feed.name}", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { ICalStore.remove(context, feed.id); onFeedsChanged() }) {
                            Icon(Icons.Outlined.Delete, "Remove ${feed.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showAddFeed = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add iCal link")
                }
                OutlinedButton(onClick = onSyncFeeds, enabled = feeds.isNotEmpty() && !syncingFeeds, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sync all")
                }
            }
            Text(
                "Only UNTIL/COUNT daily and weekly repeats expand; monthly rules and time-zone files are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showAddFeed) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var testing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!testing) showAddFeed = false },
            title = { Text("Add iCal link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; error = null }, label = { Text("Name (e.g. School)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it; error = null }, label = { Text("iCal URL (https://…)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !testing && url.isNotBlank(),
                    onClick = {
                        scope.launch {
                            testing = true
                            error = null
                            try {
                                val feed = withContext(Dispatchers.IO) {
                                    val f = ICalStore.add(context, name.ifBlank { url }, url)
                                    val r = ICalStore.refresh(context, f)
                                    r.getOrThrow()
                                    f
                                }
                                onFeedsChanged()
                                showAddFeed = false
                                Toast.makeText(context, "${feed.name} added", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                error = (e.message ?: "Could not add this link.").take(220)
                            } finally {
                                testing = false
                            }
                        }
                    },
                ) { Text(if (testing) "Checking…" else "Add") }
            },
            dismissButton = { TextButton(onClick = { showAddFeed = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AgendaEventRow(color: androidx.compose.ui.graphics.Color, title: String, meta: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
