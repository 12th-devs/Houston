package com.astroboii47.commander

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Memories tab: library, collections, search, trash. */
@Composable
fun MemoriesPage(
    memories: List<MemoryRecord>,
    collections: List<MemoryCollection>,
    trash: List<MemoryRecord>,
    query: String,
    onQuery: (String) -> Unit,
    onSave: (MemoryRecord) -> Unit,
    onTrash: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpen: (MemoryRecord) -> Unit,
) {
    var showCapture by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var filterCol by remember { mutableStateOf<String?>(null) }

    SectionCard(
        icon = Icons.Outlined.Search,
        title = "Search memories",
        trailing = {
            OutlinedButton(onClick = { showCapture = true }) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Capture")
            }
        },
    ) {
        OutlinedTextField(
            query, onQuery,
            label = { Text("Search — exact or by meaning…") },
            placeholder = { Text("“breaststroke technique”, “Houston deadline worries”…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (collections.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filterCol == null, onClick = { filterCol = null }, label = { Text("All") })
                collections.take(4).forEach { c ->
                    FilterChip(selected = filterCol == c.uuid, onClick = { filterCol = if (filterCol == c.uuid) null else c.uuid }, label = { Text(c.name) })
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    val visible = remember(memories, filterCol) {
        if (filterCol == null) memories else memories.filter { filterCol in it.collections }
    }
    SectionCard(icon = Icons.Outlined.Book, title = "Library (${visible.size})") {
        if (visible.isEmpty()) {
            Text(
                if (query.isNotBlank()) "No memories match — try fewer words."
                else "No memories yet — capture a note, voice reflection, photo, or shared link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.take(20).forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(m) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title.ifBlank { m.body.take(60) }.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                    )
                    val sub = listOfNotNull(
                        m.summary.takeIf { it.isNotBlank() }?.take(90) ?: m.body.take(90).takeIf { it.isNotBlank() },
                        m.tags.take(3).joinToString(", ").takeIf { it.isNotEmpty() },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (m.analysisStatus == MemoryAnalysisStatus.FAILED) {
                        Text(
                            "Analysis failed — open to retry. Your text is intact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                IconButton(onClick = { onToggleFavorite(m.uuid) }) {
                    Icon(
                        if (m.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        if (m.favorite) "Unfavorite" else "Favorite",
                        tint = if (m.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onTrash(m.uuid) }) {
                    Icon(Icons.Outlined.Delete, "Trash memory", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (trash.isNotEmpty()) {
            TextButton(onClick = { showTrash = !showTrash }) {
                Text(if (showTrash) "Hide trash (${trash.size})" else "Trash (${trash.size})")
            }
            if (showTrash) {
                trash.take(10).forEach { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.title.ifBlank { m.body.take(40) }.ifEmpty { "Untitled" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRestore(m.uuid) }) {
                            Icon(Icons.Outlined.RestoreFromTrash, "Restore")
                        }
                        IconButton(onClick = { onDelete(m.uuid) }) {
                            Icon(Icons.Outlined.Delete, "Delete permanently", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showCapture) {
        var body by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCapture = false },
            title = { Text("Capture memory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text("What should Houston remember?") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Text(
                        "Saved first, organized after. Analysis is optional and on-device by default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = body.isNotBlank(),
                    onClick = {
                        onSave(MemoryRecord(title = title.trim(), body = body.trim(), source = "manual"))
                        showCapture = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCapture = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun MemoryDetailSheet(
    memory: MemoryRecord,
    collections: List<MemoryCollection>,
    onSave: (MemoryRecord) -> Unit,
    onClose: () -> Unit,
    onRetryAnalysis: (String) -> Unit,
    onViewImage: (java.io.File) -> Unit = {},
) {
    val context = LocalContext.current
    var note by remember(memory) { mutableStateOf(memory.note) }
    var fav by remember(memory) { mutableStateOf(memory.favorite) }
    var images by remember(memory) { mutableStateOf(memory.imageUris) }
    var voices by remember(memory) { mutableStateOf(memory.voiceUris) }
    var recording by remember { mutableStateOf(false) }
    var playingRef by remember { mutableStateOf<String?>(null) }
    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            MemoryMedia.importImage(context, uri)?.let { name ->
                images = (images + "$name").distinct().take(10)
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder.start()) recording = true
    }
    fun persist(extra: MemoryRecord.() -> MemoryRecord = { this }) {
        onSave(memory.copy(note = note, favorite = fav, imageUris = images, voiceUris = voices).extra())
    }
    SectionCard(icon = Icons.Outlined.Book, title = memory.title.ifBlank { "Memory" }) {
        if (memory.body.isNotBlank()) {
            Text("Original", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(memory.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (memory.summary.isNotBlank()) {
            Text("Summary (generated)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(memory.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (memory.tags.isNotEmpty()) {
            Text("Tags: ${memory.tags.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (memory.analysisStatus == MemoryAnalysisStatus.FAILED) {
            Text(
                "Analysis: ${memory.analysisError ?: "failed"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { onRetryAnalysis(memory.uuid) }) { Text("Retry analysis") }
        }
        OutlinedTextField(
            note, { note = it },
            label = { Text("Your note (kept separate from generated text)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        // Attachments: photos (tap to zoom) + voice notes.
        if (images.isNotEmpty() || voices.isNotEmpty()) {
            Text(
                "Attachments (${images.size + voices.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        images.forEach { ref ->
            val f = remember(ref) { MemoryMedia.resolveAny(context, ref) }
            if (f != null) {
                val thumb = remember(ref) {
                    runCatching {
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                        android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)?.asImageBitmap()
                    }.getOrNull()
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (thumb != null) {
                        Image(
                            thumb, "Attached photo",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { onViewImage(f) },
                        )
                    } else {
                        TextButton(onClick = { onViewImage(f) }) { Text("View photo") }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Photo · tap to zoom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        images = images - ref
                        f.delete()
                        persist()
                    }) {
                        Icon(Icons.Outlined.Delete, "Remove photo", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        voices.forEach { ref ->
            val f = remember(ref) { MemoryMedia.resolveAny(context, ref) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (f != null) {
                        playingRef = if (player.toggle(f)) ref else null
                    }
                }) {
                    Icon(
                        if (playingRef == ref) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        if (playingRef == ref) "Stop voice note" else "Play voice note",
                    )
                }
                Text(
                    "Voice note${if (playingRef == ref) " · playing" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (playingRef == ref) {
                        player.stop()
                        playingRef = null
                    }
                    voices = voices - ref
                    f?.delete()
                    persist()
                }) {
                    Icon(Icons.Outlined.Delete, "Remove voice note", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickImage.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Photo, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Photo")
            }
            OutlinedButton(
                onClick = {
                    if (recording) {
                        recorder.stop()?.let { name -> voices = (voices + "$name").distinct().take(5) }
                        recording = false
                        persist()
                    } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        if (recorder.start()) recording = true
                    } else {
                        micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    null, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (recording) "Stop" else "Voice")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { fav = !fav; persist() },
                modifier = Modifier.weight(1f),
            ) { Text(if (fav) "Unfavorite" else "Favorite") }
            OutlinedButton(onClick = { persist(); onClose() }, modifier = Modifier.weight(1f)) {
                Text("Done")
            }
        }
        collections.filter { it.uuid in memory.collections }.takeIf { it.isNotEmpty() }?.let { cols ->
            Text(
                "In: ${cols.joinToString(", ") { it.name }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
