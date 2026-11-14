package com.astroboii47.commander

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Book
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

/** Daily briefing card: Calendar → School → Swimming → Memories → More. */
@Composable
fun BriefingCard(
    briefing: DailyBriefing,
    lastSyncLabel: String?,
    onRefresh: () -> Unit,
) {
    SectionCard(
        icon = Icons.Outlined.Lightbulb,
        title = briefing.greeting,
        trailing = {
            TextButton(onClick = onRefresh) { Text("Refresh") }
        },
    ) {
        if (briefing.sections.size == 1 && briefing.sections[0].heading == "Today") {
            Text(
                briefing.sections[0].lines.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            briefing.sections.forEach { s ->
                Text(
                    s.heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                s.lines.take(6).forEach { line ->
                    Text(
                        "• $line",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }
        }
        lastSyncLabel?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                "Last sync: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun InsightListCard(
    insights: List<Insight>,
    onAck: (String) -> Unit,
    onOpen: (Insight) -> Unit,
) {
    if (insights.isEmpty()) return
    SectionCard(icon = Icons.Outlined.Lightbulb, title = "Insights") {
        insights.take(5).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (s.kind) {
                        InsightKind.SCHOOL -> Icons.Outlined.School
                        InsightKind.SWIM -> Icons.Outlined.Waves
                        InsightKind.MEMORY -> Icons.Outlined.Book
                        InsightKind.CALENDAR -> Icons.Outlined.CalendarMonth
                        else -> Icons.Outlined.Lightbulb
                    },
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        s.explanation.take(160),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.actionTarget != null) {
                    TextButton(onClick = { onOpen(s) }) { Text(s.actionLabel ?: "Open") }
                }
                IconButton(onClick = { onAck(s.id) }) {
                    Icon(Icons.Outlined.Check, "Dismiss insight: ${s.title}")
                }
            }
        }
    }
}
