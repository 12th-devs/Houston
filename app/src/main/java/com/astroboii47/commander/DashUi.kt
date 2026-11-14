package com.astroboii47.commander

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MONTH_TITLE_FMT = DateTimeFormatter.ofPattern("MMMM yyyy")

fun psUserMessage(e: PsException): String = when (e) {
    is PsException.InvalidCredentials -> "PowerSchool rejected the username or password."
    is PsException.SessionExpired -> "PowerSchool session expired — sign in again."
    is PsException.MfaRequired -> e.message ?: "PowerSchool asked for a verification code this app can't complete."
    is PsException.SsoRequired -> "PowerSchool redirected to a school login page this app can't complete."
    is PsException.Parse -> "Couldn't read a PowerSchool page (${(e.message ?: "").take(120)})."
    is PsException.Network -> "Network trouble talking to PowerSchool${e.status?.let { " (HTTP $it)" } ?: ""}."
}

fun psScoreText(a: PsAssignment): String {
    val e = a.earned ?: return ""
    val p = a.possible ?: return ""
    fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    return "${fmt(e)}/${fmt(p)}"
}

fun relDayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}

@Composable
fun gradeColor(letter: String): Color {
    return when (letter.uppercase().firstOrNull()) {
        'A' -> Color(0xFF2E7D32)
        'B' -> Color(0xFF558B2F)
        'C' -> Color(0xFFF9A825)
        'D', 'F' -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
}

/** Feed colors cycle through theme roles — never fixed hues. */
@Composable
fun rememberFeedPalette(): List<Color> =
    listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)

@Composable
fun WaveformMark(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bars = listOf(0.35f, 0.7f, 1f, 0.6f, 0.85f)
    val cols = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.tertiary, scheme.primary)
    Canvas(modifier.size(34.dp, 30.dp)) {
        val bw = size.width / (bars.size * 2f)
        bars.forEachIndexed { i, f ->
            val h = size.height * f
            drawRoundRect(
                color = cols[i % cols.size],
                topLeft = Offset(i * 2 * bw + bw * 0.25f, (size.height - h) / 2),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2, bw / 2),
            )
        }
    }
}

@Composable
fun HeaderWaves(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(w * 0.42f, 0f)
                cubicTo(w * 0.62f, h * 0.25f, w * 0.55f, h * 0.55f, w * 0.78f, h * 0.72f)
                cubicTo(w * 0.95f, h * 0.85f, w * 0.98f, h, w, h)
                lineTo(w, 0f)
                close()
            },
            color = primary.copy(alpha = .16f),
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.66f, 0f)
                cubicTo(w * 0.8f, h * 0.3f, w * 0.78f, h * 0.6f, w, h * 0.82f)
                lineTo(w, 0f)
                close()
            },
            color = tertiary.copy(alpha = .20f),
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.88f, 0f)
                cubicTo(w * 0.94f, h * 0.35f, w * 0.93f, h * 0.6f, w, h)
                lineTo(w, 0f)
                close()
            },
            color = primary.copy(alpha = .22f),
        )
    }
}

@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    viewAllLabel: String? = null,
    onViewAll: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (trailing != null) trailing()
                if (viewAllLabel != null && onViewAll != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onViewAll,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(viewAllLabel, fontSize = 13.sp)
                        Icon(
                            Icons.Outlined.ChevronRight, null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
}

/**
 * Month grid shared by Home and the Calendar page. Sunday-first, no day
 * borders — selection is a tint, today is a filled pill.
 */
@Composable
fun MonthGridCard(
    month: YearMonth,
    selectedDay: LocalDate,
    today: LocalDate,
    dots: Map<LocalDate, List<Color>>,
    legend: List<Pair<String, Color>>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7 // Sunday-first
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(onClick = onPrev) { Text("‹", fontSize = 22.sp) }
                Text(
                    month.format(MONTH_TITLE_FMT),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                androidx.compose.material3.TextButton(onClick = onNext) { Text("›", fontSize = 22.sp) }
                androidx.compose.material3.Button(
                    onClick = onToday,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("Today", fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(
                        d, modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                        textAlign = TextAlign.Center, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            padded.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val selected = date == selectedDay
                        val isToday = date != null && date == today
                        val dayDots = date?.let { dots[it] }.orEmpty().take(4)
                        Box(
                            Modifier.weight(1f).height(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    when {
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = .18f)
                                        else -> Color.Transparent
                                    },
                                )
                                .then(if (date != null) Modifier.clickable { onSelect(date) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                        dayDots.forEach { c ->
                                            Box(Modifier.size(4.dp).clip(CircleShape).background(c))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (legend.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    legend.take(4).forEachIndexed { i, (name, color) ->
                        if (i > 0) Spacer(Modifier.width(14.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(5.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
