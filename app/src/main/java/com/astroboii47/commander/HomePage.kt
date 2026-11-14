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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Home tab: the summary hub. Calendar + School tabs are extended versions. */
@Composable
fun HomePage(
    feeds: List<ICalFeed>,
    feedsVersion: Int,
    psCourses: List<PsCourse>,
    selectedTerm: String?,
    gpa: Double?,
    gpaDelta: Double?,
    letterCounts: List<Pair<String, Int>>,
    movers: List<PsGradeMove>,
    moverDate: (PsGradeMove) -> String?,
    swim: SwimState,
    swimExpanded: Boolean,
    onToggleSwimExpanded: () -> Unit,
    onSaveSwimTargets: (String, Double?, Double?, Double?) -> Unit,
    onAddSwimEntry: (String, String, String?) -> Unit,
    onDeleteSwimEntry: (String) -> Unit,
    onOpenCalendar: (LocalDate) -> Unit,
    onOpenSchool: () -> Unit,
    briefing: DailyBriefing? = null,
    insights: List<Insight> = emptyList(),
    weekly: BriefingSection? = null,
    lastSyncLabel: String? = null,
    onRefreshAll: () -> Unit = {},
    onAckInsight: (String) -> Unit = {},
    onOpenInsight: (Insight) -> Unit = {},
    onReflect: () -> Unit = {},
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    val palette = rememberFeedPalette()
    val schoolDot = MaterialTheme.colorScheme.secondary

    val monthStart = remember(month) { month.atDay(1) }
    val monthEnd = remember(month) { month.atEndOfMonth() }
    val icalOccurrences = remember(feedsVersion, feeds, monthStart, monthEnd) {
        val from = monthStart.minusWeeks(1)
        val to = monthEnd.plusWeeks(1)
        runCatching { ICalStore.occurrences(context, from, to) }.getOrDefault(emptyList())
    }
    val dots = remember(icalOccurrences, psCourses) {
        val map = mutableMapOf<LocalDate, MutableList<androidx.compose.ui.graphics.Color>>()
        for (o in icalOccurrences) {
            val idx = feeds.indexOfFirst { it.id == o.feedId }.takeIf { it >= 0 } ?: 0
            map.getOrPut(o.date) { mutableListOf() }.add(palette[idx % palette.size])
        }
        for (a in PowerSchoolStore.distinctAssignments(psCourses)) {
            val d = a.dueIso?.let { psIsoToLocalDate(it) } ?: continue
            map.getOrPut(d) { mutableListOf() }.add(schoolDot)
        }
        map
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

    // Selected-day agenda (compact).
    val dayIcal = remember(selectedDay, icalOccurrences) {
        icalOccurrences.filter { it.date == selectedDay }
    }
    val dayPs = remember(selectedDay, psCourses) { PowerSchoolStore.dueOn(psCourses, selectedDay) }
    if (dayIcal.isNotEmpty() || dayPs.isNotEmpty()) {
        SectionCard(icon = Icons.Outlined.Event, title = selectedDay.format(DateTimeFormatter.ofPattern("EEE, MMM d"))) {
            dayIcal.take(4).forEach { o ->
                AgendaRow(
                    dotColor = palette[(feeds.indexOfFirst { it.id == o.feedId }.takeIf { it >= 0 } ?: 0) % palette.size],
                    title = o.summary,
                    subtitle = listOfNotNull(
                        if (o.allDay) "All day"
                        else o.startTime?.let { s ->
                            val e = o.endTime
                            if (e != null && e != s) "${s.format(DateTimeFormatter.ofPattern("h:mm a"))} – ${e.format(DateTimeFormatter.ofPattern("h:mm a"))}"
                            else s.format(DateTimeFormatter.ofPattern("h:mm a"))
                        },
                        o.location?.takeIf { it.isNotBlank() },
                        o.feedName,
                    ).joinToString(" · "),
                )
            }
            dayPs.take(4).forEach { a ->
                AgendaRow(
                    dotColor = if (a.status == "missing") MaterialTheme.colorScheme.error else schoolDot,
                    title = a.name ?: "Assignment",
                    subtitle = buildString {
                        append(a.courseName)
                        a.dueMdy?.let { append(" · due $it") }
                        val s = psScoreText(a)
                        if (s.isNotEmpty()) append(" · $s") else append(" · ${a.status}")
                    },
                )
            }
            if (dayIcal.size + dayPs.size > 4) {
                androidx.compose.material3.TextButton(onClick = { onOpenCalendar(selectedDay) }) {
                    Text("See full day in Calendar")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // Intelligent daily briefing (Calendar → School → Swimming → Memories).
    if (briefing != null) {
        BriefingCard(briefing = briefing, lastSyncLabel = lastSyncLabel, onRefresh = onRefreshAll)
        Spacer(Modifier.height(12.dp))
    }
    if (insights.isNotEmpty()) {
        InsightListCard(insights = insights, onAck = onAckInsight, onOpen = onOpenInsight)
        Spacer(Modifier.height(12.dp))
    }

    // School summary.
    SectionCard(
        icon = Icons.Outlined.School,
        title = "School",
        viewAllLabel = "Open",
        onViewAll = onOpenSchool,
    ) {
        if (psCourses.isEmpty()) {
            Text(
                "Connect PowerSchool in the School tab to see grades here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GPA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            gpa?.let { "%.2f".format(it) } ?: "—",
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        )
                        if (gpaDelta != null && kotlin.math.abs(gpaDelta) >= 0.005) {
                            Spacer(Modifier.width(8.dp))
                            val up = gpaDelta > 0
                            Icon(
                                if (up) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                null,
                                tint = if (up) gradeColor("A") else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                (if (up) "+" else "") + "%.2f".format(gpaDelta),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (up) gradeColor("A") else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Box(Modifier.width(1.dp).height(52.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Classes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            psCourses.filter { selectedTerm == null || it.term == selectedTerm }.size.toString(),
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        letterCounts.take(3).forEach { (letter, n) ->
                            val c = gradeColor(letter)
                            Text(
                                "$n ${letter}'s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c,
                                modifier = Modifier.background(c.copy(alpha = .16f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
            if (movers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                movers.take(2).forEach { m ->
                    val down = m.delta < 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (down) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.primaryContainer,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (down) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp,
                                null,
                                tint = if (down) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.courseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            fun pct(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
                            Text(
                                (if (down) "Dropped " else "+") + "${if (down) pct(-m.delta) else pct(m.delta)}% (${pct(m.oldPercent)} → ${pct(m.newPercent)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            val nextUp = remember(psCourses, selectedTerm) {
                PowerSchoolStore.upcoming(
                    if (selectedTerm == null) psCourses else psCourses.filter { it.term == selectedTerm },
                    LocalDate.now(), 14,
                ).filter { it.status != "missing" }.take(2)
            }
            nextUp.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            (a.name ?: "Assignment") + " (${a.courseName})",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Due ${a.dueIso?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.let { relDayLabel(it) } } ?: (a.dueMdy ?: "undated")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    SwimCard(
        state = swim,
        expanded = swimExpanded,
        onToggleExpanded = onToggleSwimExpanded,
        onSaveTargets = onSaveSwimTargets,
        onAddEntry = onAddSwimEntry,
        onDeleteEntry = onDeleteSwimEntry,
    )
    Spacer(Modifier.height(12.dp))

    SectionCard(
        icon = Icons.Outlined.Nightlight,
        title = "Evening reflection",
        viewAllLabel = "Reflect",
        onViewAll = onReflect,
    ) {
        Text(
            "A line or two about today — saved as a memory, never a nag.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (weekly != null) {
        Spacer(Modifier.height(12.dp))
        WeeklyReviewCard(week = weekly)
    }
}

@Composable
private fun AgendaRow(dotColor: androidx.compose.ui.graphics.Color, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
