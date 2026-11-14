package com.astroboii47.commander

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** School tab: term grades, class list, tap through to full assignment detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolPage(
    psUi: Any,
    progress: String,
    courses: List<PsCourse>,
    allCourses: List<PsCourse>,
    refreshedAt: String?,
    username: String,
    password: String,
    server: String,
    showPassword: Boolean,
    selectedTerm: String?,
    terms: List<String>,
    gpa: Double?,
    gpaDelta: Double?,
    letterCounts: List<Pair<String, Int>>,
    movers: List<PsGradeMove>,
    moverDate: (PsGradeMove) -> String?,
    openCourseId: String?,
    onOpenCourse: (String?) -> Unit,
    onTerm: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onServer: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    BackHandler(enabled = openCourseId != null) { onOpenCourse(null) }
    val openCourse = remember(openCourseId, allCourses) {
        openCourseId?.let { id -> allCourses.firstOrNull { it.id == id } }
    }
    if (openCourse != null) {
        ClassDetail(course = openCourse, onBack = { onOpenCourse(null) })
        return
    }
    when (psUi) {
        is DashboardPsUi.SignedOut -> AuthCard(
            username, password, server, showPassword,
            onUsername, onPassword, onServer, onTogglePassword, onSignIn,
        )
        is DashboardPsUi.SigningIn -> SectionCard(icon = Icons.Outlined.School, title = "School") {
            Text(psUi.note.ifEmpty { "Working…" }, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (progress.isNotEmpty()) {
                Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is DashboardPsUi.Content -> SchoolContent(
            courses = courses,
            refreshedAt = refreshedAt,
            terms = terms,
            selectedTerm = selectedTerm,
            gpa = gpa,
            gpaDelta = gpaDelta,
            letterCounts = letterCounts,
            movers = movers,
            moverDate = moverDate,
            failure = (psUi as? DashboardPsUi.Content)?.error,
            syncing = (psUi as? DashboardPsUi.Content)?.syncing == true,
            onTerm = onTerm,
            onRefresh = onRefresh,
            onSignOut = onSignOut,
            onOpenCourse = onOpenCourse,
        )
    }
}

/** Small view-state mirror so SchoolPage stays decoupled from dashboard internals. */
sealed interface DashboardPsUi {
    data object SignedOut : DashboardPsUi
    data class SigningIn(val note: String) : DashboardPsUi
    data class Content(val error: String?, val syncing: Boolean, val stale: Boolean) : DashboardPsUi
}

@Composable
private fun AuthCard(
    username: String,
    password: String,
    server: String,
    showPassword: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onServer: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSignIn: () -> Unit,
) {
    SectionCard(icon = Icons.Outlined.School, title = "School") {
        Text("Connect PowerSchool", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Guardian sign-in for grades and assignments. Only session cookies stay on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedTextField(username, onUsername, label = { Text("Parent username") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password, onPassword, label = { Text("Password (never stored)") },
            visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(server, onServer, label = { Text("Server") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTogglePassword) { Text(if (showPassword) "Hide password" else "Show password") }
            Button(onClick = onSignIn, enabled = username.isNotBlank() && password.isNotEmpty()) { Text("Sign in & sync") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolContent(
    courses: List<PsCourse>,
    refreshedAt: String?,
    terms: List<String>,
    selectedTerm: String?,
    gpa: Double?,
    gpaDelta: Double?,
    letterCounts: List<Pair<String, Int>>,
    movers: List<PsGradeMove>,
    moverDate: (PsGradeMove) -> String?,
    failure: String?,
    syncing: Boolean,
    onTerm: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpenCourse: (String) -> Unit,
) {
    SectionCard(icon = Icons.Outlined.School, title = "School") {
        if (failure != null) {
            Text(failure, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Grades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, "Refresh PowerSchool", tint = MaterialTheme.colorScheme.primary)
            }
        }
        refreshedAt?.take(10)?.let {
            Text("Synced $it · portal grades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text("GPA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(gpa?.let { "%.2f".format(it) } ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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
            Box(Modifier.width(1.dp).height(52.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Classes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(courses.size.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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
        if (terms.size > 1) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                terms.forEach { t ->
                    FilterChip(selected = t == selectedTerm, onClick = { onTerm(t) }, label = { Text(t) })
                }
            }
        }
        if (movers.isNotEmpty()) {
            Text("Moving", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            movers.forEach { m ->
                val down = m.delta < 0
                val dateLabel = moverDate(m)?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> relDayLabel(d) } } ?: "Recently"
                fun pct(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (down) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (down) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp,
                            null,
                            tint = if (down) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.courseName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (down) "Dropped " else "+") + "${if (down) pct(-m.delta) else pct(m.delta)}% (${pct(m.oldPercent)} → ${pct(m.newPercent)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Classes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        if (courses.isEmpty()) {
            Text("No courses for this term yet — refresh to sync.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        courses.forEach { c ->
            val missing = c.assignments.count { it.status == "missing" }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenCourse(c.id) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background((c.gradeLetter?.let { gradeColor(it) } ?: MaterialTheme.colorScheme.primary).copy(alpha = .16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        c.gradeLetter ?: "–",
                        fontWeight = FontWeight.Bold,
                        color = c.gradeLetter?.let { gradeColor(it) } ?: MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(c.teacher, c.expression).joinToString(" · ")
                    Text(
                        if (missing > 0) "$missing missing · $sub" else sub.ifEmpty { "${c.assignments.size} assignments" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (missing > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    c.gradePercent?.let { "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)}%" } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            refreshedAt?.let {
                Text("Synced ${it.take(16).replace('T', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

@Composable
private fun ClassDetail(course: PsCourse, onBack: () -> Unit) {
    val missing = course.assignments.filter { it.status == "missing" }.sortedBy { it.dueIso ?: "9999" }
    val upcoming = course.assignments.filter { it.status != "missing" && it.status != "graded" && it.status != "submitted" }
        .sortedBy { it.dueIso ?: "9999" }
    val graded = course.assignments.filter { it.status == "graded" || it.status == "submitted" }
        .sortedByDescending { it.dueIso ?: "" }.take(40)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to classes") }
            Text("Class", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(course.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                listOfNotNull(course.teacher, course.expression, course.term)
                    .joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        course.gradeLetter ?: "—",
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                        color = course.gradeLetter?.let { gradeColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                    )
                    course.gradePercent?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${course.assignments.size} assignments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (missing.isNotEmpty()) {
                            Text("${missing.size} missing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (course.assignments.isEmpty()) {
            SectionCard(icon = Icons.Outlined.Event, title = "Assignments") {
                Text("No assignments in this snapshot — refresh to sync.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        if (missing.isNotEmpty()) {
            SectionCard(icon = Icons.Outlined.Event, title = "Missing (${missing.size})") {
                missing.forEach { AssignmentRow(it) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (upcoming.isNotEmpty()) {
            SectionCard(icon = Icons.Outlined.Event, title = "Upcoming (${upcoming.size})") {
                upcoming.forEach { AssignmentRow(it) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (graded.isNotEmpty()) {
            SectionCard(icon = Icons.Outlined.Event, title = "Graded") {
                graded.forEach { AssignmentRow(it) }
            }
        }
    }
}

@Composable
private fun AssignmentRow(a: PsAssignment) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(
                    when (a.status) {
                        "missing" -> MaterialTheme.colorScheme.error
                        "late" -> MaterialTheme.colorScheme.tertiary
                        "exempt" -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.primary
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name ?: "Assignment", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    a.category?.let { append(it) }
                    val s = psScoreText(a)
                    if (s.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(s)
                    }
                    if (a.percent != null) {
                        if (isNotEmpty()) append(" ")
                        append("(${if (a.percent % 1.0 == 0.0) a.percent.toInt().toString() else "%.1f".format(a.percent)}%)")
                    }
                    a.letter?.let {
                        if (isNotEmpty()) append(" ")
                        append(it)
                    }
                    if (a.flags.contains("excluded from final grade")) append(" · excluded")
                    else append(" · ${a.status}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (a.status == "missing") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                a.dueMdy ?: "No date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            a.letter?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = gradeColor(it))
            }
        }
    }
}
