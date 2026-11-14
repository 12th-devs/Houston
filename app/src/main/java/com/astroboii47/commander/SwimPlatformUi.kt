package com.astroboii47.commander

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Full Swim tab: profile, goals, practice journal, results, analytics. */
@Composable
fun SwimPlatformPage(
    profile: SwimmerProfile,
    goals: List<SwimGoal>,
    practices: List<PracticeSession>,
    results: List<RaceResult>,
    lastSync: String?,
    meetStatus: String?,
    onSaveProfile: (SwimmerProfile) -> Unit,
    onSaveGoal: (SwimGoal) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddPractice: (PracticeSession) -> Unit,
    onDeletePractice: (String) -> Unit,
    onAddResult: (RaceResult) -> Unit,
    onConnectResults: () -> Unit,
) {
    var showProfile by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf<SwimGoal?>(null) }
    var showNewGoal by remember { mutableStateOf(false) }
    var showPractice by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    SectionCard(
        icon = Icons.Outlined.Waves,
        title = "Swimmer",
        viewAllLabel = "Edit",
        onViewAll = { showProfile = true },
    ) {
        val name = profile.name.ifBlank { "Set up your profile" }
        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            listOf(profile.club, profile.schoolTeam, swimCourseLabel(profile.preferredCourse))
                .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Club, school, preferred course" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profile.meetMobileSwimmerId.isNotBlank()) {
            Text(
                "Meet Mobile linked (verified id)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    SectionCard(icon = Icons.Outlined.EmojiEvents, title = "Goals (${goals.size})") {
        if (goals.isEmpty()) {
            Text(
                "No goals yet — add one like “100 Breast · SCY · 1:05.00”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        goals.forEach { g ->
            val progress = swimGoalProgress(g)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Best ${g.currentBestSec?.let { formatSwimTime(it) } ?: "—"} → Goal ${formatSwimTime(g.targetSec)}" +
                            (if (g.completed) " · Done" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (progress ?: 0f).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    progress?.let { "${(it * 100).toInt()}%" } ?: "—",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                if (g.completed) {
                    Icon(Icons.Outlined.CheckCircle, "Goal completed", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showGoal = g }) {
                    Icon(Icons.Outlined.Edit, "Edit goal ${g.label}")
                }
                IconButton(onClick = { onDeleteGoal(g.id) }) {
                    Icon(Icons.Outlined.Delete, "Delete goal ${g.label}", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showNewGoal = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add goal")
            }
            OutlinedButton(onClick = onConnectResults, modifier = Modifier.weight(1f)) {
                Text("Connect results")
            }
        }
        meetStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        lastSync?.let {
            Text("Results synced $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(12.dp))

    SectionCard(icon = Icons.Outlined.Waves, title = "Practice (${practices.size})") {
        if (practices.isEmpty()) {
            Text(
                "Log your first practice — templates make it one tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        practices.take(5).forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.notes.ifBlank { "${p.distanceYd?.let { "$it yd · " } ?: ""}${swimCourseLabel(p.poolCourse)}" },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    )
                    val sub = listOfNotNull(
                        p.dateIso,
                        p.distanceYd?.let { "$it yd" },
                        p.durationMin?.let { "$it min" },
                        p.rpe?.let { "RPE $it" },
                        p.strokes.takeIf { it.isNotEmpty() }?.joinToString("/"),
                        if (p.voiceRef.isNotBlank()) "voice note" else null,
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onDeletePractice(p.id) }) {
                    Icon(Icons.Outlined.Delete, "Delete practice", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedButton(onClick = { showPractice = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Log practice")
        }
    }
    Spacer(Modifier.height(12.dp))

    SectionCard(icon = Icons.Outlined.EmojiEvents, title = "Results (${results.size})") {
        if (results.isEmpty()) {
            Text(
                "No verified results yet — import from Meet Mobile or add manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results.take(6).forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    "${r.eventName} · ${r.timeSec?.let { formatSwimTime(it) } ?: "—"}${r.place?.let { " · #$it" } ?: ""}${if (r.dq) " · DQ" else ""}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${r.meetName} · ${swimCourseLabel(r.course)} · ${if (r.provenance.startsWith("meetmobile")) "Meet Mobile" else "manual"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = { showResult = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add result manually")
        }
    }
    Spacer(Modifier.height(12.dp))

    val volume = remember(practices) { weeklyYardage(practices) }
    if (volume.isNotEmpty()) {
        SectionCard(icon = Icons.Outlined.Waves, title = "Training volume") {
            val max = (volume.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            volume.takeLast(6).forEach { (week, yd) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(week, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                    LinearProgressIndicator(
                        progress = { (yd.toFloat() / max).coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("$yd", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Observed training load only — not a coaching claim.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showProfile) {
        var name by remember(profile) { mutableStateOf(profile.name) }
        var club by remember(profile) { mutableStateOf(profile.club) }
        var school by remember(profile) { mutableStateOf(profile.schoolTeam) }
        var mmid by remember(profile) { mutableStateOf(profile.meetMobileSwimmerId) }
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = { Text("Swimmer profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(club, { club = it }, label = { Text("Club") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(school, { school = it }, label = { Text("School team") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(mmid, { mmid = it }, label = { Text("Meet Mobile id (verified only)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveProfile(profile.copy(name = name.trim(), club = club.trim(), schoolTeam = school.trim(), meetMobileSwimmerId = mmid.trim()))
                    showProfile = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showProfile = false }) { Text("Cancel") } },
        )
    }

    if (showNewGoal || showGoal != null) {
        val editing = showGoal
        var event by remember { mutableStateOf(editing?.event ?: "") }
        var course by remember { mutableStateOf(editing?.course ?: profile.preferredCourse) }
        var target by remember { mutableStateOf(editing?.let { formatSwimTime(it.targetSec) } ?: "") }
        var start by remember { mutableStateOf(editing?.startSec?.let { formatSwimTime(it) } ?: "") }
        var best by remember { mutableStateOf(editing?.currentBestSec?.let { formatSwimTime(it) } ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewGoal = false; showGoal = null },
            title = { Text(if (editing == null) "Add goal" else "Edit goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(event, { event = it }, label = { Text("Event (e.g. 100 Breast)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SwimCourse.entries.forEach { c ->
                            FilterChip(selected = course == c, onClick = { course = c }, label = { Text(swimCourseLabel(c)) })
                        }
                    }
                    OutlinedTextField(target, { target = it; error = null }, label = { Text("Target (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it }, label = { Text("Season start (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(best, { best = it }, label = { Text("Current best (optional)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = parseSwimTime(target)
                    if (event.isBlank() || t == null) {
                        error = "Event and a valid target like 1:05.00 are required."
                    } else {
                        val base = editing ?: SwimGoal(id = newSwimGoalId(), event = event.trim(), course = course, targetSec = t)
                        onSaveGoal(
                            base.copy(
                                event = event.trim(), course = course, targetSec = t,
                                startSec = if (start.isBlank()) null else parseSwimTime(start),
                                currentBestSec = if (best.isBlank()) null else parseSwimTime(best),
                            ),
                        )
                        showNewGoal = false; showGoal = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNewGoal = false; showGoal = null }) { Text("Cancel") } },
        )
    }

    if (showPractice) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var distance by remember { mutableStateOf("") }
        var duration by remember { mutableStateOf("") }
        var sets by remember { mutableStateOf("") }
        var rpe by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var voiceRef by remember { mutableStateOf("") }
        var recording by remember { mutableStateOf(false) }
        var template by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val recorder = remember { VoiceRecorder(context) }
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && recorder.start()) recording = true
        }
        AlertDialog(
            onDismissRequest = { showPractice = false },
            title = { Text("Log practice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PRACTICE_TEMPLATES.forEachIndexed { i, (name, _) ->
                            FilterChip(selected = template == i, onClick = { template = i; sets = PRACTICE_TEMPLATES[i].second }, label = { Text(name) })
                        }
                    }
                    OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(distance, { distance = it }, label = { Text("Distance (yd)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(duration, { duration = it }, label = { Text("Duration (min)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sets, { sets = it }, label = { Text("Sets") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(rpe, { rpe = it }, label = { Text("Effort RPE 1–10") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes / reflection") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = {
                            if (recording) {
                                recorder.stop()?.let { name -> voiceRef = "$name" }
                                recording = false
                            } else if (androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO,
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                if (recorder.start()) recording = true
                            } else {
                                micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            null, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                recording -> "Stop voice note"
                                voiceRef.isNotBlank() -> "Voice attached · tap to re-record"
                                else -> "Attach voice note"
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddPractice(
                        PracticeSession(
                            id = newPracticeId(),
                            dateIso = date,
                            poolCourse = profile.preferredCourse,
                            distanceYd = distance.toIntOrNull(),
                            durationMin = duration.toIntOrNull(),
                            sets = sets,
                            rpe = rpe.toIntOrNull()?.coerceIn(1, 10),
                            notes = notes,
                            voiceRef = voiceRef,
                        ),
                    )
                    showPractice = false
                }) { Text("Log") }
            },
            dismissButton = { TextButton(onClick = { showPractice = false }) { Text("Cancel") } },
        )
    }

    if (showResult) {
        var event by remember { mutableStateOf("") }
        var course by remember { mutableStateOf(profile.preferredCourse) }
        var time by remember { mutableStateOf("") }
        var meet by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showResult = false },
            title = { Text("Add result manually") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(event, { event = it }, label = { Text("Event (e.g. 100 Breast)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SwimCourse.entries.forEach { c ->
                            FilterChip(selected = course == c, onClick = { course = c }, label = { Text(swimCourseLabel(c)) })
                        }
                    }
                    OutlinedTextField(time, { time = it }, label = { Text("Time (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(meet, { meet = it }, label = { Text("Meet name") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = parseSwimTime(time)
                    if (event.isBlank() || t == null) {
                        error = "Event and a valid time are required."
                    } else {
                        val key = resultDedupeKey("manual", event.trim(), course, t, profile.name)
                        onAddResult(
                            RaceResult(
                                id = key, meetId = "manual", meetName = meet.ifBlank { "Manual entry" },
                                eventId = event.trim(), eventName = event.trim(), course = course,
                                timeSec = t, place = null, dateIso = LocalDate.now().toString(),
                                swimmerRef = profile.name, provenance = "manual",
                            ),
                        )
                        showResult = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showResult = false }) { Text("Cancel") } },
        )
    }
}
