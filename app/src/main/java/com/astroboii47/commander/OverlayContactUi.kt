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

// Contact overlay UI (SMS/Houston only, no Hub). Extracted verbatim from CommandOverlayUi.kt (no logic edits).
@Composable
internal fun OverlayContactAccess(palette: OverlayPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(palette.tile)
            .border(1.dp, OverlayAccent.copy(alpha = .7f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("@", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 21.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("allow contact search", color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("needed for names, calls and messages", color = palette.secondary, fontFamily = OverlayMono, fontSize = 11.sp)
        }
        Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    }
}

@Composable
internal fun OverlayContactResults(
    contacts: List<ContactResult>,
    parsed: ParsedCommand,
    selectedIndex: Int,
    visibleRowCount: Int,
    palette: OverlayPalette,
    onSelect: (ContactResult) -> Unit,
) {
    if (contacts.isEmpty()) {
        OverlayNotice("no matching contacts", palette)
        return
    }
    // Simplified contact rows: SMS / Houston contacts only. No Hub keys, no
    // reply badges, no recent-messages destination. Grouping is by Houston
    // person id when present, otherwise each row is its own group.
    data class ContactGroup(val key: String, val personName: String?, val routes: List<IndexedValue<ContactResult>>)
    val groups = remember(contacts) {
        val commanderIdByName = contacts.asSequence()
            .filter { it.commanderMessagingContactId != null }
            .associate { OverlayContactFilter.canonicalize(it.name) to it.commanderMessagingContactId!! }
        val grouped = linkedMapOf<String, MutableList<IndexedValue<ContactResult>>>()
        contacts.forEachIndexed { index, contact ->
            val canonicalName = OverlayContactFilter.canonicalize(contact.name)
            val personId = contact.commanderMessagingContactId ?: commanderIdByName[canonicalName]
            val key = personId?.let { "person:$it" }
                ?: "route:${contact.messagingPlatformScope ?: contact.messagingIdentity ?: "$index:${contact.name}:${contact.number}"}"
            grouped.getOrPut(key) { mutableListOf() }.add(IndexedValue(index, contact))
        }
        grouped.map { (key, routes) ->
            val orderedRoutes = routes.sortedByDescending { it.value.messagingIsDefault }
            ContactGroup(key, key.takeIf { it.startsWith("person:") }?.let { orderedRoutes.first().value.name }, orderedRoutes)
        }
    }
    val selectedGroupIndex = groups.indexOfFirst { group -> group.routes.any { it.index == selectedIndex } }.coerceAtLeast(0)
    val displayUnits = contacts.size + groups.count { it.personName != null }
    // Shared selection/visibility handling with OverlayFileResults via keepSelectionVisible.
    val listState = rememberResultListState(selectedGroupIndex, groups.size, visibleRowCount.coerceAtLeast(1))
    LazyColumn(
        Modifier.fillMaxWidth().height(resultListHeight(displayUnits, visibleRowCount)),
        state = listState,
        contentPadding = PaddingValues(bottom = ResultViewportBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
    itemsIndexed(groups, key = { _, group -> group.key }) { groupIndex, group ->
        val first = group.routes.first().value
        val shape = RoundedCornerShape(15.dp)
            if (group.personName != null) {
                Column(Modifier.fillMaxWidth().clip(shape).background(palette.tile).border(1.dp, palette.border, shape).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, top = 0.dp, end = 2.dp, bottom = 6.dp)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(palette.border), contentAlignment = Alignment.Center) {
                            first.avatar?.let { Image(BitmapPainter(it.asImageBitmap()), first.name, Modifier.fillMaxSize()) }
                                ?: Text(first.name.take(1).uppercase(), color = Color.White, fontFamily = OverlaySans, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(group.personName, color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${group.routes.size} messaging ${if (group.routes.size == 1) "route" else "routes"}", color = palette.secondary, fontFamily = OverlayMono, fontSize = 10.sp)
                        }
                    }
                    group.routes.forEach { indexed ->
                        val contact = indexed.value
                        val selected = indexed.index == selectedIndex
                        val routeShape = RoundedCornerShape(11.dp)
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            if (selected) Box(Modifier.matchParentSize().blur(8.dp, BlurredEdgeTreatment.Unbounded).border(2.dp, OverlayAccent.copy(alpha = .8f), routeShape))
                            Row(
                                Modifier.fillMaxWidth().clip(routeShape)
                                    .background(if (selected) OverlayAccent.copy(alpha = .20f) else Color.Transparent)
                                    .border(1.dp, if (selected) OverlayAccent else palette.border.copy(alpha = .65f), routeShape)
                                    .clickable { onSelect(contact) }.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(OverlayAccent.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                                    MessagingPlatformGlyph(contact)
                                }
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(contact.messagingPlatform ?: "Message", color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(contact.label.substringAfter(" · ", contact.label), color = palette.secondary, fontFamily = OverlayMono, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (selected) Text("↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                        }
                    }
                }
            } else {
                val contact = first
                val selected = group.routes.first().index == selectedIndex
                Box(Modifier.fillMaxWidth()) {
                    if (selected) Box(Modifier.matchParentSize().blur(10.dp, BlurredEdgeTreatment.Unbounded).border(3.dp, OverlayAccent.copy(alpha = .82f), shape))
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(palette.tile)
                            .then(if (selected) Modifier.background(OverlayAccent.copy(alpha = .20f)) else Modifier)
                            .border(1.dp, if (selected) OverlayAccent else palette.border, shape)
                            .clickable { onSelect(contact) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val platformDestination = contact.messagingPlatformScope != null
                        Box(
                            Modifier.size(40.dp).clip(if (platformDestination) RoundedCornerShape(11.dp) else CircleShape)
                                .background(if (platformDestination) OverlayAccent.copy(alpha = .14f) else if (selected) OverlayAccent else palette.border),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (platformDestination) MessagingPlatformGlyph(contact)
                            else if (contact.avatar != null) Image(BitmapPainter(contact.avatar.asImageBitmap()), contact.name, Modifier.fillMaxSize())
                            else Text(contact.name.take(1).uppercase(), color = Color.White, fontFamily = OverlaySans, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.padding(start = 13.dp).weight(1f)) {
                            Text(contact.name, color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(contact.label, color = palette.secondary, fontFamily = OverlayMono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (selected) Text("  ↵", color = OverlayAccent, fontFamily = OverlayMono, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessagingPlatformGlyph(contact: ContactResult, compact: Boolean = false) {
    val platform = contact.messagingPlatform
    val iconSize = if (compact) 11.dp else 21.dp
    if (contact.messagingPlatformIcon != null) {
        Image(
            painter = BitmapPainter(contact.messagingPlatformIcon.asImageBitmap()),
            contentDescription = platform ?: "Messaging app",
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(OverlayAccent),
            modifier = Modifier.size(iconSize),
        )
        return
    }
    val icon = when {
        contact.messagingPackageName == "com.instagram.android" || platform.equals("instagram", true) -> Icons.Outlined.CameraAlt
        contact.messagingPackageName == "com.Slack" || platform.equals("slack", true) -> Icons.Outlined.Tag
        contact.messagingPackageName == "com.microsoft.teams" || platform?.contains("teams", true) == true -> Icons.Outlined.Groups
        else -> when (platform?.lowercase()) {
        "sms", "messages" -> Icons.Outlined.ChatBubbleOutline
        "whatsapp" -> Icons.Outlined.PhoneInTalk
        "messenger" -> Icons.Outlined.Bolt
        "signal" -> Icons.Outlined.Sensors
        "telegram" -> Icons.Outlined.Send
        "discord" -> Icons.Outlined.Forum
            else -> null
        }
    }
    if (icon != null) {
        Icon(icon, platform ?: "Messaging app", tint = OverlayAccent, modifier = Modifier.size(iconSize))
    } else {
        Icon(Icons.Outlined.ChatBubbleOutline, platform ?: "Messaging app", tint = OverlayAccent, modifier = Modifier.size(iconSize))
    }
}

@Composable
internal fun OverlaySelectedContact(contact: ContactResult, kind: CommandKind, palette: OverlayPalette) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 3 },
    ) {
        val shape = RoundedCornerShape(15.dp)
        Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().blur(10.dp, BlurredEdgeTreatment.Unbounded)
                .border(3.dp, OverlayAccent.copy(alpha = .82f), shape),
        )
        Row(
            Modifier.fillMaxWidth().clip(shape).background(palette.tile)
                .background(OverlayAccent.copy(alpha = .20f))
                .border(1.dp, OverlayAccent.copy(alpha = .7f), shape)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(OverlayAccent),
                contentAlignment = Alignment.Center,
            ) {
                if (contact.avatar != null) Image(
                    painter = BitmapPainter(contact.avatar.asImageBitmap()),
                    contentDescription = contact.name,
                    modifier = Modifier.fillMaxSize(),
                ) else Text(contact.name.take(1).uppercase(), color = Color.White, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (contact.messagingPlatform != null || contact.messagingPackageName != null) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(palette.tile)
                            .border(1.dp, OverlayAccent.copy(alpha = .7f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        MessagingPlatformGlyph(contact, compact = true)
                    }
                }
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(contact.name, color = palette.text, fontFamily = OverlaySans, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (kind == CommandKind.Message) "type message${contact.messagingPlatform?.let { " · $it" }.orEmpty()}" else "ready to call",
                    color = palette.secondary,
                    fontFamily = OverlayMono,
                    fontSize = 11.sp,
                )
            }
            Text(contact.label, color = palette.secondary, fontFamily = OverlayMono, fontSize = 12.sp)
        }
        }
    }
}

/**
 * Pure contact filter/rank helpers (no Android deps) for unit testing.
 * Extracted from OverlayContactResults grouping logic verbatim semantics.
 */
internal object OverlayContactFilter {
    internal fun canonicalize(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    internal fun matches(name: String, label: String, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) || label.lowercase().contains(q)
    }

    internal fun rank(name: String, query: String): Int {
        if (query.isBlank()) return 0
        val n = name.lowercase()
        val q = query.trim().lowercase()
        return when {
            n == q -> 0
            n.startsWith(q) -> 1
            n.contains(q) -> 2
            else -> 3
        }
    }

    internal fun filterAndSort(names: List<String>, query: String): List<String> {
        if (query.isBlank()) return names.sortedBy { it.lowercase() }
        return names.filter { matches(it, it, query) }
            .sortedWith(compareBy({ rank(it, query) }, { it.length }, { it.lowercase() }))
    }
}
