package com.astroboii47.commander.secondbrain.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astroboii47.commander.SectionCard
import com.astroboii47.commander.secondbrain.context.BrainSnapshot

/**
 * Home — personal intelligence. Greeting, one AI briefing, Up Next,
 * max three ranked priorities, universal input. No widget soup.
 */
@Composable
fun BrainHome(
    snapshot: BrainSnapshot?,
    briefing: String?,
    briefingLoading: Boolean,
    onRegenerateBriefing: () -> Unit,
    onOpenRadar: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onAsk: (String) -> Unit,
) {
    // 1. Contextual greeting — minimal, no oversized header.
    Text(
        (snapshot?.greeting ?: "Hello.") + (snapshot?.let { " It's ${it.dateLabel}." } ?: ""),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))

    // 2. AI intelligence briefing — the primary element.
    SectionCard(icon = Icons.Outlined.AutoAwesome, title = "Briefing") {
        when {
            briefingLoading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading your day…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            briefing != null -> Text(briefing, style = MaterialTheme.typography.bodyLarge)
            else -> Text(
                "Connect a calendar or sync school to fill this in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRegenerateBriefing) { Text("Refresh briefing") }
    }
    Spacer(Modifier.height(12.dp))

    // 3. Up Next — one relevant item only.
    val next = snapshot?.today?.firstOrNull { !it.title.isBlank() }
        ?: snapshot?.upcoming?.firstOrNull()
    if (next != null) {
        SectionCard(icon = Icons.AutoMirrored.Outlined.ArrowForward, title = "Up next") {
            Text(next.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (next.detail.isNotBlank()) {
                Text(next.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenPlan) { Text("Open plan") }
        }
        Spacer(Modifier.height(12.dp))
    }

    // 4. On Your Radar — max three, ranked.
    val radar = snapshot?.radar?.take(3).orEmpty()
    if (radar.isNotEmpty()) {
        SectionCard(icon = Icons.Outlined.AutoAwesome, title = "On your radar") {
            radar.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (item.detail.isNotBlank()) {
                            Text(
                                item.detail.take(120),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onOpenRadar(item.sourceRef) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Open ${item.title}")
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    // 5. Universal input.
    BrainBar(onSubmit = onAsk)
}
