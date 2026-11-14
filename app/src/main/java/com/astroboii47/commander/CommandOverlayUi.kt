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

internal val OverlayAccent: Color get() = AccentSettings.color.value
internal val OverlayMono = FontFamily(
    Font(R.font.courier_prime_regular, FontWeight.Normal),
    Font(R.font.courier_prime_bold, FontWeight.Bold),
)
internal val OverlaySans = FontFamily(Font(R.font.inter_variable))
internal val OverlaySerif = FontFamily(Font(R.font.instrument_serif_regular))
internal const val InputSentinel = '\u2060'
internal val ResultViewportBottomPadding = 10.dp

internal enum class FileAction(val title: String, val subtitle: String) {
    Open("Open", "open with the default app"),
    OpenWith("Open with…", "choose another app"),
    Reveal("Reveal in folder", "show the containing folder"),
    Preview("Preview", "view without leaving Houston"),
    Info("File info", "type, size, location and date"),
    Delete("Delete", "permanently delete this file"),
}
internal enum class AppListMode { All, Recents }
internal enum class TodoistPage(val title: String) {
    Today("tasks"),
    Upcoming("upcoming"),
    Calendar("calendar"),
}

internal data class OverlayPalette(
    val panel: Color,
    val tile: Color,
    val text: Color,
    val secondary: Color,
    val border: Color,
)

internal fun resultViewportHeight(visibleRowCount: Int) =
    (visibleRowCount * 66 + (visibleRowCount - 1).coerceAtLeast(0) * 8).dp

/**
 * Shared lazy-list keyboard/selection handler for result rows (files + contacts).
 * Both lists keep the selected row visible and report visible indices for
 * icon/preview prefetch. File filtering/sorting lives in the caller via
 * [FileSortMode]; contact grouping lives in [OverlayContactResults] and is
 * keyed only on SMS/Houston identity (no Hub keys, no reply badges).
 *
 * Global UI state note: no `object X { var y by mutableStateOf }` remains for
 * Hub/notification state (hub store deleted). Remaining globals are
 * intentional singletons observed as State: CommanderMessagingContacts.revision,
 * AccentSettings.color, AppearanceSettings.*, TodoistTasks cache, etc.
 */
@Composable
internal fun rememberResultListState(
    selectedIndex: Int,
    itemCount: Int,
    visibleRowCount: Int,
    onVisibleIndicesChanged: ((List<Int>) -> Unit)? = null,
): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(selectedIndex, itemCount) {
        if (selectedIndex in 0 until itemCount) state.keepSelectionVisible(selectedIndex, visibleRowCount)
    }
    if (onVisibleIndicesChanged != null) {
        LaunchedEffect(state, itemCount) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect(onVisibleIndicesChanged)
        }
    }
    return state
}

internal fun resultListHeight(itemCount: Int, visibleRowCount: Int) =
    resultViewportHeight(minOf(itemCount, visibleRowCount).coerceAtLeast(1)) + ResultViewportBottomPadding

internal suspend fun LazyListState.keepSelectionVisible(index: Int, visibleRowCount: Int) {
    if (index == 0) {
        scrollToItem(0)
        return
    }
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) {
        scrollToItem(index)
        return
    }
    val selected = visible.firstOrNull { it.index == index }
    val fullyVisible = selected != null && selected.offset >= layoutInfo.viewportStartOffset &&
        selected.offset + selected.size <= layoutInfo.viewportEndOffset
    if (fullyVisible) return
    val target = if (index < firstVisibleItemIndex) index else (index - visibleRowCount + 1).coerceAtLeast(0)
    animateScrollToItem(target)
}

@Composable
internal fun OverlayNotice(text: String, palette: OverlayPalette) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.tile).border(1.dp, palette.border, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text(text, color = palette.secondary, fontFamily = OverlayMono, fontSize = 13.sp)
    }
}

@Composable
internal fun RealAppIcon(drawable: Drawable?, label: String, palette: OverlayPalette, modifier: Modifier = Modifier) {
    val painter = remember(drawable) {
        drawable?.let { BitmapPainter(it.toBitmap(width = 96, height = 96).asImageBitmap()) }
    }
    if (painter != null) {
        Image(painter = painter, contentDescription = label, modifier = modifier.clip(RoundedCornerShape(10.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(10.dp)).background(palette.border), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun overlayActionLabel(kind: CommandKind): String = when (kind) {
    CommandKind.Apps -> "open app"
    CommandKind.Files -> "open file"
    CommandKind.Message -> "text contact"
    CommandKind.Call -> "call contact"
    CommandKind.Todo -> "add to todoist"
    CommandKind.Note -> "keep note"
    CommandKind.Event -> "create event"
    CommandKind.Timer -> "set timer or alarm"
    CommandKind.Calculator -> "calculate"
    CommandKind.Web -> "search web"
    CommandKind.Ask -> "ask Gemini AI"
}

internal fun overlayPendingLabel(kind: CommandKind): String = when (kind) {
    CommandKind.Apps -> "opening app"
    CommandKind.Files -> "opening file"
    CommandKind.Message -> "message prepared"
    CommandKind.Call -> "opening call"
    CommandKind.Todo -> "sending to todoist"
    CommandKind.Note -> "opening note"
    CommandKind.Event -> "opening event"
    CommandKind.Timer -> "timer added"
    CommandKind.Calculator -> "calculating"
    CommandKind.Web -> "opening browser"
    CommandKind.Ask -> "opening ChatGPT"
}

// Thin facade: implementation lives in OverlayController.kt + Overlay*Ui.kt.
// Kept for one release so OverlayActivity / NativeSearchActivity keep compiling.
@Composable
fun CommandOverlayApp(
    engine: CommandEngine,
    onDismiss: () -> Unit,
    initialQuery: String = "",
    initialAskPreview: String? = null,
    initialAskPrompt: String? = null,
    initialAskWaiting: Boolean = false,
    initialSwitcherMode: Boolean = false,
    switcherRequestToken: Int = 0,
    initialAssistContext: AssistantContext? = null,
) {
    OverlayControllerRoot(
        engine = engine,
        onDismiss = onDismiss,
        initialQuery = initialQuery,
        initialAskPreview = initialAskPreview,
        initialAskPrompt = initialAskPrompt,
        initialAskWaiting = initialAskWaiting,
        initialSwitcherMode = initialSwitcherMode,
        switcherRequestToken = switcherRequestToken,
        initialAssistContext = initialAssistContext
    )
}
