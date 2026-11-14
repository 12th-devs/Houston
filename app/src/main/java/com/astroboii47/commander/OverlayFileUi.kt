package com.astroboii47.commander

import android.content.res.Configuration
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged

// File overlay UI. Extracted verbatim from CommandOverlayUi.kt (no logic edits).
@Composable
internal fun OverlayFileResults(
    files: List<FileResult>,
    selectedIndex: Int,
    visibleRowCount: Int,
    palette: OverlayPalette,
    sortMode: FileSortMode,
    listState: LazyListState,
    onVisibleIndicesChanged: (List<Int>) -> Unit,
    onLongClick: (FileResult) -> Unit,
    onClick: (FileResult) -> Unit,
) {
    if (files.isEmpty()) {
        OverlayNotice("no matching files", palette)
        return
    }
    LaunchedEffect(selectedIndex, files.firstOrNull()?.uri, files.size) {
        if (selectedIndex in files.indices) listState.keepSelectionVisible(selectedIndex, visibleRowCount)
    }
    LaunchedEffect(listState, files.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect(onVisibleIndicesChanged)
    }
    LazyColumn(
        Modifier.fillMaxWidth().height(resultListHeight(files.size, visibleRowCount)),
        state = listState,
        contentPadding = PaddingValues(bottom = ResultViewportBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    itemsIndexed(files, key = { index, file -> "${file.uri}:$index" }) { index, file ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.tile)
                .border(1.dp, if (index == selectedIndex) OverlayAccent.copy(alpha = .7f) else palette.border, RoundedCornerShape(15.dp))
                .combinedClickable(onClick = { onClick(file) }, onLongClick = { onLongClick(file) }).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (file.thumbnail != null) Image(
                BitmapPainter(file.thumbnail.asImageBitmap()), file.name, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)),
            ) else Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (index == selectedIndex) OverlayAccent.copy(alpha = .16f) else palette.panel),
                contentAlignment = Alignment.Center,
            ) {
                if (file.isDirectory) Icon(
                    Icons.Rounded.Folder,
                    contentDescription = "Folder",
                    tint = if (index == selectedIndex) OverlayAccent else palette.secondary,
                    modifier = Modifier.size(22.dp),
                ) else Icon(
                    fileTypeIcon(file),
                    contentDescription = file.mimeType ?: "File",
                    tint = if (index == selectedIndex) OverlayAccent else palette.secondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(file.name, color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(file.location.ifBlank { if (file.isDirectory) "folder" else file.mimeType ?: "file" }, color = palette.secondary, fontFamily = OverlayMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (sortMode == FileSortMode.Relevance) {
                if (index == selectedIndex) Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            } else Column(
                modifier = Modifier.padding(start = 8.dp).widthIn(min = 72.dp, max = 96.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    modifiedLabel(file.modifiedAtMillis),
                    color = palette.secondary,
                    fontFamily = OverlayMono,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (index == selectedIndex) Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "File actions",
                tint = if (index == selectedIndex) OverlayAccent else palette.secondary.copy(alpha = .72f),
                modifier = Modifier.padding(start = 5.dp).size(25.dp).clickable { onLongClick(file) },
            )
        }
    }
    }
}

internal fun fileTypeIcon(file: FileResult): ImageVector {
    val mime = file.mimeType.orEmpty().lowercase()
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when {
        mime == "application/pdf" || extension == "pdf" -> Icons.Outlined.PictureAsPdf
        extension in setOf("epub", "mobi", "azw", "azw3", "fb2") -> Icons.Outlined.MenuBook
        mime.startsWith("audio/") -> Icons.Outlined.AudioFile
        mime.startsWith("video/") -> Icons.Outlined.VideoFile
        mime.contains("zip") || mime.contains("compressed") || extension in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") -> Icons.Outlined.Archive
        mime.contains("spreadsheet") || mime.contains("excel") || extension in setOf("xls", "xlsx", "ods", "csv", "tsv") -> Icons.Outlined.TableChart
        mime.contains("presentation") || mime.contains("powerpoint") || extension in setOf("ppt", "pptx", "odp") -> Icons.Outlined.Slideshow
        mime.startsWith("text/") && extension in setOf("kt", "java", "js", "ts", "py", "html", "css", "json", "xml", "md", "sh", "yaml", "yml") -> Icons.Outlined.Code
        mime.startsWith("text/") || mime.contains("document") || mime.contains("word") || extension in setOf("doc", "docx", "odt", "rtf", "txt") -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}

@Composable
internal fun OverlayFileActions(
    target: FileResult,
    actions: List<FileAction>,
    selectedIndex: Int,
    visibleRowCount: Int,
    palette: OverlayPalette,
    onClick: (FileAction) -> Unit,
) {
    if (actions.isEmpty()) {
        OverlayNotice("no matching actions", palette)
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(selectedIndex, actions.size) {
        if (selectedIndex in actions.indices) state.keepSelectionVisible(selectedIndex, visibleRowCount)
    }
    LazyColumn(
        Modifier.fillMaxWidth().height(resultListHeight(actions.size, visibleRowCount)),
        state = state,
        contentPadding = PaddingValues(bottom = ResultViewportBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(actions, key = { _, action -> action.name }) { index, action ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.tile)
                    .border(1.dp, if (index == selectedIndex) OverlayAccent.copy(alpha = .76f) else palette.border, RoundedCornerShape(15.dp))
                    .clickable { onClick(action) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (index == selectedIndex) OverlayAccent.copy(alpha = .16f) else palette.panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (action) {
                            FileAction.Open -> Icons.Outlined.InsertDriveFile
                            FileAction.OpenWith -> Icons.Outlined.OpenInNew
                            FileAction.Reveal -> Icons.Outlined.FolderOpen
                            FileAction.Preview -> Icons.Outlined.Visibility
                            FileAction.Info -> Icons.Outlined.Info
                            FileAction.Delete -> Icons.Outlined.Delete
                        },
                        contentDescription = action.title,
                        tint = if (action == FileAction.Delete) Color(0xFFFF4D3A) else if (index == selectedIndex) OverlayAccent else palette.secondary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(action.title, color = if (action == FileAction.Delete) Color(0xFFFF4D3A) else palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(action.subtitle, color = palette.secondary, fontFamily = OverlayMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (index == selectedIndex) Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
    }
}

@Composable
internal fun OverlayFileActionDetail(
    target: FileResult,
    detail: String,
    preview: android.graphics.Bitmap?,
    palette: OverlayPalette,
    onConfirmDelete: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.tile)
            .border(1.dp, OverlayAccent.copy(alpha = .65f), RoundedCornerShape(15.dp)).padding(14.dp),
    ) {
        Text(target.name, color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        if (preview != null) {
            Image(
                preview.asImageBitmap(),
                target.name,
                Modifier.fillMaxWidth().heightIn(min = 130.dp, max = 250.dp).clip(RoundedCornerShape(11.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(detail, color = palette.secondary, fontFamily = OverlayMono, fontSize = 12.sp, lineHeight = 18.sp)
            if (onConfirmDelete != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "delete file",
                    color = Color(0xFFFF4D3A),
                    fontFamily = OverlayMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFFFF4D3A).copy(alpha = .72f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onConfirmDelete)
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
    }
}

internal fun modifiedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "date unknown"
    val age = System.currentTimeMillis() - timestamp
    return if (age in 0 until 7L * 24 * 60 * 60 * 1000) {
        android.text.format.DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            android.text.format.DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } else {
        java.text.SimpleDateFormat("d MMM yy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

@Composable
internal fun OverlayFileAccess(palette: OverlayPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.tile)
            .border(1.dp, OverlayAccent.copy(alpha = .7f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▤", color = OverlayAccent, fontFamily = OverlayMono, fontSize = 21.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("allow file search", color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("one-time Android storage access", color = palette.secondary, fontFamily = OverlayMono, fontSize = 11.sp)
        }
        Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    }
}
