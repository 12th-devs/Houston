package com.astroboii47.commander.secondbrain.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astroboii47.commander.VoiceInput

/**
 * Universal AI input: ask, capture, command. Voice uses the system
 * recognizer (no audio stays in Houston); results flow through Ask.
 */
@Composable
fun BrainBar(
    hint: String = "Ask anything, or tell me to remember…",
    initial: String = "",
    enabled: Boolean = true,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var text by remember(initial) { mutableStateOf(initial) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        VoiceInput.parseResult(result.data)?.let { heard ->
            if (heard.isNotBlank()) {
                if (text.isBlank()) onSubmit(heard) else onSubmit(text.trim() + " " + heard)
                text = ""
            }
        }
    }
    val activity = context as? Activity
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = enabled && activity != null,
                onClick = {
                    if (activity != null) {
                        VoiceInput.startListening(activity, voiceLauncher)
                    }
                },
            ) {
                Icon(Icons.Outlined.Mic, "Voice input")
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                enabled = enabled && text.isNotBlank(),
                onClick = { onSubmit(text.trim()); text = "" },
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, "Send")
            }
        }
    }
}
