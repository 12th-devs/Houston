package com.astroboii47.commander

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Optional end-of-day reflection. Never nags: one entry point, skip anytime. */
@Composable
fun EveningReflectionDialog(
    onSave: (text: String, voiceRef: String?) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var voiceRef by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(context) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start()) recording = true
    }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Reflect on today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it },
                    label = { Text("A line or two — what mattered today?") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3,
                )
                OutlinedButton(
                    onClick = {
                        if (recording) {
                            recorder.stop()?.let { voiceRef = "$it" }
                            recording = false
                        } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
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
                            recording -> "Stop recording"
                            voiceRef != null -> "Voice attached · re-record?"
                            else -> "Or record a voice reflection"
                        },
                    )
                }
                Text(
                    "Saved as a memory. Skip or disable anytime — Houston won't nag.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() || voiceRef != null,
                onClick = { onSave(text.trim(), voiceRef) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        },
    )
}

@Composable
fun WeeklyReviewCard(week: BriefingSection, onDismiss: () -> Unit = {}) {
    SectionCard(icon = Icons.Outlined.AutoStories, title = "Weekly review · ${week.heading}") {
        week.lines.take(14).forEach { line ->
            Text(
                if (line.startsWith("•")) line else "— $line",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
        if (onDismiss != {}) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
