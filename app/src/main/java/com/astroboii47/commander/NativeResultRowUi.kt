package com.astroboii47.commander

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MarkdownText(value: String) {
    val context = LocalContext.current
    val renderer = remember(context) { io.noties.markwon.Markwon.create(context) }
    AndroidView(factory = { android.widget.TextView(it).apply {
        setTextColor(AndroidColor.WHITE)
        textSize = 14f
        setLinkTextColor(AndroidColor.rgb(185, 167, 255))
        movementMethod = android.text.method.LinkMovementMethod.getInstance()
    } }, update = { renderer.setMarkdown(it, value) }, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun NativeResultRow(result: NativeResult, index: Int, engine: CommandEngine, player: CommandBarDeviceActions.Player?, selected: Boolean, modifier: Modifier = Modifier) {
    val icon = when (result.kind) {
        NativeKind.Weather -> Icons.Outlined.WbSunny
        NativeKind.BrowseFiles, NativeKind.FileAccess -> Icons.Outlined.Folder
        NativeKind.File -> Icons.Outlined.Description
        NativeKind.DownloadsMenu -> Icons.Outlined.Download
        NativeKind.OpenSettings, NativeKind.Setting -> Icons.Outlined.Settings
        NativeKind.CommanderSetting -> Icons.Outlined.Tune
        NativeKind.Calculator -> Icons.Outlined.Calculate
        NativeKind.Web -> Icons.Outlined.Public
        NativeKind.ScreenshotMenu, NativeKind.ScreenshotAccess, NativeKind.Screenshot -> Icons.Outlined.PhotoLibrary
        NativeKind.MusicMenu -> Icons.Outlined.MusicNote
        NativeKind.AiMenu, NativeKind.AiPrompt -> Icons.Outlined.AutoAwesome
        NativeKind.Brightness -> Icons.Outlined.Brightness6
        NativeKind.Volume -> Icons.Outlined.VolumeUp
        NativeKind.ScreenshotCapture -> Icons.Outlined.Screenshot
        NativeKind.BluetoothToggle, NativeKind.BluetoothSettings, NativeKind.BluetoothDevice, NativeKind.BluetoothAccess -> Icons.Outlined.Bluetooth
        NativeKind.MusicPrevious -> Icons.Outlined.SkipPrevious
        NativeKind.MusicNext -> Icons.Outlined.SkipNext
        NativeKind.MusicToggle -> if (player?.playing == true) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
        NativeKind.MusicOpen -> Icons.Outlined.OpenInNew
        NativeKind.Clipboard, NativeKind.ClipboardMenu -> Icons.Outlined.ContentPaste
        NativeKind.SearchHistory -> Icons.Outlined.Search
        NativeKind.Command -> when (parseCommand(result.title.substringAfter("Run command: ")).kind) {
            CommandKind.Note -> Icons.Outlined.NoteAdd
            CommandKind.Call -> Icons.Outlined.Phone
            CommandKind.Message -> Icons.Outlined.Send
            CommandKind.Timer -> Icons.Outlined.Alarm
            CommandKind.Event -> Icons.Outlined.Event
            CommandKind.Ask -> Icons.Outlined.AutoAwesome
            else -> Icons.Outlined.Search
        }
        else -> Icons.Outlined.Search
    }
    val context = LocalContext.current
    val bitmap = remember(result.app?.packageName) {
        result.app?.packageName?.let(engine::loadPackageIcon)?.toBitmap(40, 40)?.asImageBitmap()
    }
    val screenshotBitmap by produceState<ImageBitmap?>(null, result.screenshot?.uri) {
        value = result.screenshot?.uri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(48, 48), null).asImageBitmap() }.getOrNull()
            }
        }
    }
    Row(
        modifier.fillMaxWidth().height(if (CommandBarPreferences.compact(engine.context)) 39.dp else 46.dp)
            .background(if (selected) Color(0x665E5F63) else Color.Transparent, RoundedCornerShape(11.dp))
            .padding(start = 1.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (screenshotBitmap != null) Image(screenshotBitmap!!, null, Modifier.size(28.dp))
        else if (bitmap != null) Image(bitmap, null, Modifier.size(28.dp))
        else Icon(icon, null, tint = if (CommandBarPreferences.materialYouBar(engine.context)) accentColor(engine.context) else Color(0xFFE9E9EA), modifier = Modifier.size(28.dp).padding(3.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(result.title, color = Color(0xFFF4F4F5), fontSize = 13.sp, lineHeight = 14.sp,
                style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result.subtitle.isNotBlank()) Text(result.subtitle, color = Color(0xFF9699A0), fontSize = 11.sp, lineHeight = 12.sp,
                style = TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (index < 10) Box(
            Modifier.size(23.dp).background(if (selected) accentColor(engine.context).copy(alpha = .35f) else Color(0xFF303136), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (index < 9) "${index + 1}" else "0", color = Color(0xFFF0EDF5), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
internal fun ScreenshotTile(result: NativeResult, index: Int, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, result.screenshot?.uri) {
        value = result.screenshot?.uri?.let { uri -> withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, android.util.Size(220, 220), null).asImageBitmap() }.getOrNull()
        } }
    }
    Column(Modifier.padding(4.dp).background(if (selected) Color(0xFF47404F) else Color(0xFF29292D), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(5.dp)) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!, null, Modifier.fillMaxSize())
            else Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White)
        }
        Text("${if (index < 9) index + 1 else if (index == 9) 0 else ""}  ${result.title}", color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun accentColor(context: android.content.Context): Color {
    if (CommandBarPreferences.materialYouBar(context) && Build.VERSION.SDK_INT >= 31)
        return androidx.compose.material3.dynamicDarkColorScheme(context).primary
    return when (CommandBarPreferences.accent(context)) {
    1 -> Color(0xFF65CFF1)
    2 -> Color(0xFFF5A96E)
    3 -> Color(0xFF8BD5A5)
    else -> Color(0xFFA892F6)
    }
}
