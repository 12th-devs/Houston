package com.astroboii47.commander

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app viewer for [AssistantLog]: read, copy, clear. Internal diagnostics UI. */
class AssistantLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LogScreen { finish() }
                }
            }
        }
    }
}

private val Mono = FontFamily.Monospace

@androidx.compose.runtime.Composable
private fun LogScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(AssistantLog.entries()) }
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LaunchedEffect(revision) {
        entries = AssistantLog.entries()
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    @androidx.compose.runtime.Composable
    fun levelColor(level: AssistantLog.Level): Color = when (level) {
        AssistantLog.Level.ERROR -> Color(0xFFFF6B68)
        AssistantLog.Level.WARN -> Color(0xFFFFB84D)
        AssistantLog.Level.INFO -> MaterialTheme.colorScheme.primary
        AssistantLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Assistant log", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text(
            "${entries.size} entries · newest at bottom",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (AssistantLog.copyToClipboard(context)) {
                    Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Copy failed", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Copy") }
            OutlinedButton(onClick = { revision++ }) { Text("Refresh") }
            OutlinedButton(onClick = {
                AssistantLog.clear()
                revision++
            }) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text(
                "No entries yet. Invoke the assistant (gesture, then back here) and tap Refresh.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries, key = { "${it.timeMillis}-${it.message.hashCode()}" }) { entry ->
                    Column {
                        Text(
                            "${timeFormat.format(Date(entry.timeMillis))} ${entry.level.name}",
                            color = levelColor(entry.level),
                            fontFamily = Mono,
                            fontSize = 10.sp,
                        )
                        Text(entry.message, fontFamily = Mono, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
