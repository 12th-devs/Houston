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

// Todoist overlay UI. Extracted verbatim from CommandOverlayUi.kt (no logic edits).
internal fun TodoistTask.isOverdue(): Boolean = dueDate?.take(10)?.let { it < java.time.LocalDate.now().toString() } == true

internal fun TodoistTask.isDueToday(): Boolean =
    dueDate?.take(10) == java.time.LocalDate.now().toString()

internal fun TodoistTask.dueDateLabel(): String {
    val date = runCatching { java.time.LocalDate.parse(dueDate?.take(10)) }.getOrNull()
    return when {
        date == null -> "Today"
        date == java.time.LocalDate.now() -> "Today"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }
}

internal fun TodoistTask.dueTimeLabel(context: android.content.Context): String? {
    val raw = dueDateTime ?: return null
    val instant = runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { java.time.LocalDateTime.parse(raw).atZone(java.time.ZoneId.systemDefault()).toInstant() }
        .getOrNull() ?: return raw.substringAfter('T', "").take(5).takeIf(String::isNotBlank)
    return android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date.from(instant))
}

internal fun TodoistTask.todoistSection(page: TodoistPage): String {
    if (isOverdue()) return "overdue"
    val date = runCatching { java.time.LocalDate.parse(dueDate?.take(10)) }.getOrNull() ?: return page.title.lowercase()
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")).lowercase()
    }
}

@Composable
internal fun OverlayTodoistCalendar(
    tasks: List<TodoistTask>,
    selectedDate: java.time.LocalDate,
    palette: OverlayPalette,
    onDate: (java.time.LocalDate) -> Unit,
) {
    val month = java.time.YearMonth.from(selectedDate)
    val counts = remember(tasks) {
        tasks.mapNotNull { it.dueDate?.take(10) }
            .groupingBy { it }
            .eachCount()
    }
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val padded = cells + List((7 - cells.size % 7) % 7) { null }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(palette.tile)
            .border(1.dp, palette.border, RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                color = OverlayAccent,
                fontFamily = OverlayMono,
                fontSize = 20.sp,
                modifier = Modifier.clickable {
                    onDate(month.minusMonths(1).atDay(selectedDate.dayOfMonth.coerceAtMost(month.minusMonths(1).lengthOfMonth())))
                }.padding(horizontal = 7.dp),
            )
            Text(
                month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
                color = palette.text,
                fontFamily = OverlaySans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "›",
                color = OverlayAccent,
                fontFamily = OverlayMono,
                fontSize = 20.sp,
                modifier = Modifier.clickable {
                    onDate(month.plusMonths(1).atDay(selectedDate.dayOfMonth.coerceAtMost(month.plusMonths(1).lengthOfMonth())))
                }.padding(horizontal = 7.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    day,
                    color = palette.secondary,
                    fontFamily = OverlayMono,
                    fontSize = 9.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                )
            }
        }
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val selected = date == selectedDate
                    val today = date == java.time.LocalDate.now()
                    val count = date?.let { counts[it.toString()] }.orZero()
                    Box(
                        Modifier.weight(1f).height(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) OverlayAccent.copy(alpha = .30f) else Color.Transparent)
                            .border(
                                if (selected || today) 1.dp else 0.dp,
                                if (selected) OverlayAccent else palette.border,
                                RoundedCornerShape(9.dp),
                            )
                            .then(if (date != null) Modifier.clickable { onDate(date) } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (selected) palette.text else palette.secondary,
                                fontFamily = OverlayMono,
                                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp,
                            )
                            if (count > 0) {
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
                                        .size(if (count > 1) 4.dp else 3.dp).clip(CircleShape).background(OverlayAccent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun Int?.orZero() = this ?: 0

@Composable
internal fun OverlayTodoistTasks(
    tasks: List<TodoistTask>,
    calendarTasks: List<TodoistTask>,
    page: TodoistPage,
    calendarDate: java.time.LocalDate,
    selectedIndex: Int,
    visibleRowCount: Int,
    palette: OverlayPalette,
    loading: Boolean,
    error: String?,
    completingTaskId: String?,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onCalendarDate: (java.time.LocalDate) -> Unit,
    onOpen: (TodoistTask) -> Unit,
    onComplete: (TodoistTask) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹", color = if (page == TodoistPage.Today) palette.border else OverlayAccent, fontFamily = OverlayMono, fontSize = 22.sp, modifier = Modifier.clickable(enabled = page != TodoistPage.Today, onClick = onPreviousPage))
        Text(page.title, color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("›", color = if (page == TodoistPage.Calendar) palette.border else OverlayAccent, fontFamily = OverlayMono, fontSize = 22.sp, modifier = Modifier.clickable(enabled = page != TodoistPage.Calendar, onClick = onNextPage))
    }
    if (page == TodoistPage.Calendar) {
        OverlayTodoistCalendar(
            tasks = calendarTasks,
            selectedDate = calendarDate,
            palette = palette,
            onDate = onCalendarDate,
        )
        Spacer(Modifier.height(8.dp))
    }
    if (tasks.isEmpty()) {
        OverlayNotice(
            error ?: if (loading) "loading ${page.title.lowercase()} tasks…"
            else if (page == TodoistPage.Calendar) "nothing due ${calendarDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}"
            else "nothing in ${page.title.lowercase()}",
            palette,
        )
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(selectedIndex, tasks.size) {
        if (selectedIndex in tasks.indices) state.keepSelectionVisible(selectedIndex, visibleRowCount)
    }
    LazyColumn(
        // Task rows can contain a section label plus separate date and time
        // metadata, so they are taller than standard app results. Let a short
        // filtered list measure to its content while keeping a bounded viewport.
        Modifier.fillMaxWidth().heightIn(
            max = resultListHeight(tasks.size, visibleRowCount) + 38.dp,
        ),
        state = state,
        contentPadding = PaddingValues(top = 8.dp, bottom = ResultViewportBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
            Column {
                val completing = task.id == completingTaskId
                val section = task.todoistSection(page)
                val previousSection = tasks.getOrNull(index - 1)?.todoistSection(page)
                if (index == 0 || section != previousSection) {
                    Row(Modifier.fillMaxWidth().padding(start = 5.dp, end = 7.dp, bottom = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val sectionColor = when (section) {
                            "overdue" -> Color(0xFFFF5B65)
                            "today" -> Color(0xFF54C98A)
                            else -> OverlayAccent
                        }
                        Text(section, color = sectionColor, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("${tasks.count { it.todoistSection(page) == section }} tasks", color = palette.secondary, fontFamily = OverlayMono, fontSize = 10.sp)
                    }
                }
                val completionGlow by animateFloatAsState(
                    targetValue = if (completing) 1f else 0f,
                    animationSpec = tween(125),
                    label = "todoist-task-completion",
                )
                val taskShape = RoundedCornerShape(15.dp)
                Box(Modifier.fillMaxWidth()) {
                    if (completionGlow > 0f) {
                        Box(
                            Modifier.matchParentSize()
                                .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                .border(4.dp, OverlayAccent.copy(alpha = .78f * completionGlow), taskShape),
                        )
                    }
                    Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(palette.tile)
                        .background(OverlayAccent.copy(alpha = .30f * completionGlow))
                        .border(1.dp, if (completing || index == selectedIndex) OverlayAccent.copy(alpha = .88f) else palette.border, taskShape)
                        .clickable { onOpen(task) }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (completing) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = "Complete task",
                        tint = when (task.priority) {
                            4 -> Color(0xFFFF5B65)
                            3 -> Color(0xFFFF9A42)
                            2 -> Color(0xFF54A8FF)
                            else -> if (index == selectedIndex) OverlayAccent else palette.secondary
                        },
                        modifier = Modifier.size(27.dp).clickable { onComplete(task) },
                    )
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(task.content, color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val secondary = listOfNotNull(task.project.takeIf(String::isNotBlank), task.labels.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")).joinToString("  ·  ")
                        if (secondary.isNotBlank()) Text(secondary, color = palette.secondary, fontFamily = OverlayMono, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (task.isOverdue() || task.isDueToday()) {
                            val dueColor = if (task.isDueToday()) Color(0xFF54C98A) else Color(0xFFFF5B65)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Event, null, tint = dueColor, modifier = Modifier.size(15.dp))
                                Text(task.dueDateLabel(), color = dueColor, fontFamily = OverlayMono, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        task.dueTimeLabel(context)?.let { dueTime ->
                            Row(Modifier.padding(top = if (task.isOverdue() || task.isDueToday()) 4.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Schedule, null, tint = palette.secondary, modifier = Modifier.size(15.dp))
                                Text(dueTime, color = palette.secondary, fontFamily = OverlayMono, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        if (task.recurring) Icon(Icons.Outlined.Repeat, "Recurring", tint = palette.secondary, modifier = Modifier.padding(top = 4.dp).size(14.dp))
                    }
                    // Keep this slot present on every row so moving selection
                    // never shifts the date and time column sideways.
                    Box(Modifier.width(29.dp), contentAlignment = Alignment.CenterEnd) {
                        if (index == selectedIndex) Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    }
                    }
                }
            }
        }
    }
}
