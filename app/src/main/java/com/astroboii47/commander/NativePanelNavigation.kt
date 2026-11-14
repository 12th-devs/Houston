package com.astroboii47.commander

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class NativeKeyBindings(
    val activate: (Int) -> Unit,
    val move: (Int) -> Unit,
    val submit: () -> Unit,
    val backspace: () -> Boolean,
    val allowDigits: Boolean = false,
)

internal enum class SearchMode { All, Files, Settings, Calculator, Web, Weather, Screenshots, Music, Clipboard, Downloads, Ai, Control, Bluetooth }

@Composable
internal fun NativeSearchPanel(
    engine: CommandEngine,
    mediaRevision: Int,
    clipboardRevision: Int,
    onDismiss: () -> Unit,
    onOpen: (NativeResult, String) -> Unit,
    onBindings: (NativeKeyBindings) -> Unit,
) {
    val sentinel = "\u200B"
    var editor by remember { mutableStateOf(TextFieldValue(sentinel, TextRange(1))) }
    val query = editor.text.removePrefix(sentinel)
    var mode by remember { mutableStateOf(SearchMode.All) }
    var fileMatches by remember { mutableStateOf<List<FileResult>>(emptyList()) }
    var screenshotMatches by remember { mutableStateOf<List<ScreenshotEntry>>(emptyList()) }
    var downloadMatches by remember { mutableStateOf<List<FileResult>>(emptyList()) }
    var player by remember { mutableStateOf<CommandBarDeviceActions.Player?>(null) }
    var selectedIndex by remember { mutableStateOf(0) }
    var control by remember { mutableStateOf<DeviceControls.Level?>(null) }
    var controlPercent by remember { mutableIntStateOf(50) }
    var controlError by remember { mutableStateOf<String?>(null) }
    var aiMessages by remember { mutableStateOf<List<AskMessage>>(emptyList()) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiToolStatus by remember { mutableStateOf<String?>(null) }
    var weatherText by remember { mutableStateOf("") }
    var aiChatId by remember { mutableStateOf<String?>(null) }
    var savedAiChats by remember { mutableStateOf(AiChatHistory.all(engine.context)) }
    var showAiHistory by remember { mutableStateOf(true) }
    val aiListState = rememberLazyListState()
    LaunchedEffect(aiMessages, aiBusy) {
        if (aiBusy && aiMessages.lastOrNull()?.role == "user") {
            val history = aiMessages
            val response = withContext(Dispatchers.IO) { AiApi.generate(engine.context, history) { tool ->
                withContext(Dispatchers.Main) { aiToolStatus = "Using $tool…" }
            } }
            response.fold(
                onSuccess = { answer ->
                    aiMessages = history + answer.attachments + AskMessage("assistant", answer.text)
                    aiChatId = AiChatHistory.save(engine.context, aiChatId, aiMessages)
                    savedAiChats = AiChatHistory.all(engine.context)
                },
                onFailure = { aiError = it.message ?: "AI request failed" },
            )
            aiToolStatus = null
            aiBusy = false
        }
    }
    LaunchedEffect(mode, query) {
        if (mode == SearchMode.Weather) {
            weatherText = "Loading weather…"
            kotlinx.coroutines.delay(300)
            weatherText = AiTools.weather(query)
        }
    }
    LaunchedEffect(mode, aiMessages, aiBusy, aiError, showAiHistory) {
        if (mode == SearchMode.Ai && !showAiHistory) {
            val lastIndex = aiMessages.size + (if (aiBusy) 1 else 0) + (if (aiError != null) 1 else 0)
            if (lastIndex > 0) {
                aiListState.scrollToItem(lastIndex)
                val item = aiListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
                val viewport = aiListState.layoutInfo.viewportEndOffset - aiListState.layoutInfo.viewportStartOffset
                if (item != null && item.size > viewport) {
                    aiListState.scrollToItem(lastIndex, item.size - viewport)
                }
            }
        }
    }
    LaunchedEffect(mediaRevision) {
        while (true) {
            player = CommandBarDeviceActions.player(engine.context)
            kotlinx.coroutines.delay(1500)
        }
    }
    val entry = remember { Animatable(-1f) }
    val opacity = remember { Animatable(0f) }
    val animationScope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { entry.animateTo(0f, spring(dampingRatio = .82f, stiffness = 400f)) }
            launch { opacity.animateTo(1f, tween(220)) }
        }
    }
    fun closeAnimated() {
        if (closing) return
        closing = true
        animationScope.launch {
            kotlinx.coroutines.coroutineScope {
                launch { opacity.animateTo(0f, tween(170)) }
                launch { entry.animateTo(-1f, spring(dampingRatio = 1f, stiffness = 550f)) }
            }
            onDismiss()
        }
    }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (CommandBarPreferences.showKeyboard(engine.context)) {
            kotlinx.coroutines.delay(180)
            keyboard?.show()
        }
    }
    LaunchedEffect(query, mode) { selectedIndex = 0 }
    LaunchedEffect(query, mode) {
        downloadMatches = if (engine.hasFileSearchAccess() && (mode == SearchMode.Downloads || (mode == SearchMode.All && CommandBarPreferences.searchFiles(engine.context))))
            CommandBarDeviceActions.downloads(engine.context, query, 40) else emptyList()
        fileMatches = emptyList()
        fileMatches = if (query.isNotBlank() && (mode == SearchMode.Files || (mode == SearchMode.All && CommandBarPreferences.searchFiles(engine.context))) && engine.hasFileSearchAccess()) {
            kotlinx.coroutines.delay(120)
            withContext(Dispatchers.IO) { engine.searchFiles(query, 16) }
        } else emptyList()
    }
    LaunchedEffect(mode, mediaRevision, query) {
        if (mode == SearchMode.Screenshots || (mode == SearchMode.All && CommandBarPreferences.searchScreenshots(engine.context))) screenshotMatches = CommandBarDeviceActions.screenshots(engine.context)
        player = CommandBarDeviceActions.player(engine.context)
    }
    val results = remember(query, mode, fileMatches, downloadMatches, screenshotMatches, player, mediaRevision, clipboardRevision) {
        when {
            mode == SearchMode.Files && !engine.hasFileSearchAccess() -> listOf(NativeResult("Allow file search", "Grant all files access in Android settings", NativeKind.FileAccess))
            mode == SearchMode.Files && query.isBlank() -> emptyList()
            mode == SearchMode.Downloads && !engine.hasFileSearchAccess() -> listOf(NativeResult("Allow file access", "Search Downloads", NativeKind.FileAccess))
            mode == SearchMode.Downloads -> downloadMatches.map { NativeResult(it.name, it.location, NativeKind.File, file = it) }
            mode == SearchMode.Settings && query.isBlank() -> engine.searchSystemSettings("").map { NativeResult(it.label, it.subtitle, NativeKind.Setting, setting = it) }
            mode == SearchMode.Calculator && query.isBlank() -> emptyList()
            mode == SearchMode.Web && query.isBlank() -> emptyList()
            mode == SearchMode.Weather -> emptyList()
            mode == SearchMode.Control -> emptyList()
            mode == SearchMode.Bluetooth -> {
                val devices = DeviceControls.bluetoothDevices(engine.context).filter { query.isBlank() || it.name.contains(query, true) }
                    .map { NativeResult(it.name, if (it.connected) "Connected Bluetooth audio · open settings" else "Paired Bluetooth device · open settings", NativeKind.BluetoothDevice) }
                if (devices.isEmpty() && !DeviceControls.bluetoothPermission(engine.context))
                    listOf(NativeResult("Allow Bluetooth access", "Show paired devices", NativeKind.BluetoothAccess))
                else devices
            }
            mode == SearchMode.Ai -> if (query.isBlank()) emptyList() else listOf(NativeResult("Ask ${AiSettings.provider(engine.context).label}: $query", AiSettings.model(engine.context), NativeKind.AiPrompt))
            mode == SearchMode.Screenshots && !CommandBarDeviceActions.imagePermissionGranted(engine.context) ->
                listOf(NativeResult("Allow screenshot access", "Choose full or selected photo access", NativeKind.ScreenshotAccess))
            mode == SearchMode.Screenshots -> screenshotMatches.filter { query.isBlank() || it.name.contains(query, true) }
                .map { NativeResult(it.name, "Tap to copy image", NativeKind.Screenshot, screenshot = it) }
            mode == SearchMode.Music -> musicResults(player).filter { query.isBlank() || it.title.contains(query, true) }
            mode == SearchMode.Clipboard -> CommandBarHistory.clipboard(engine.context).filter { query.isBlank() || it.contains(query, true) }.map {
                NativeResult(it.replace('\n', ' ').take(90), "Clipboard · tap to copy", NativeKind.Clipboard, clipboard = it)
            }
            query.isBlank() -> {
                val apps = AppUsageRanker.ranked(engine.context, engine.allApps("")).take(12).map { NativeResult(it.label, "App", NativeKind.App, app = it) }
                val commands = listOf(
                    NativeResult("Search Files", "Files", NativeKind.BrowseFiles),
                    NativeResult("Downloads", "Browse downloaded files", NativeKind.DownloadsMenu),
                    NativeResult("Screenshots", "Images · tap to copy", NativeKind.ScreenshotMenu),
                    NativeResult("Clipboard history", "Saved text clips", NativeKind.ClipboardMenu),
                    NativeResult("Ask AI", "${AiSettings.provider(engine.context).label} · ${AiSettings.model(engine.context)}", NativeKind.AiMenu),
                    NativeResult("Weather", "Current conditions", NativeKind.Weather),
                    NativeResult("Brightness", "Adjust screen brightness", NativeKind.Brightness),
                    NativeResult("Volume", "Adjust media volume", NativeKind.Volume),
                    NativeResult("Take screenshot", "Save current screen · Shizuku", NativeKind.ScreenshotCapture),
                    NativeResult("Bluetooth", "Turn Bluetooth on or off", NativeKind.BluetoothToggle),
                    NativeResult("Bluetooth devices", "Manage paired devices", NativeKind.BluetoothSettings),
                    NativeResult("Houston settings", "Appearance and search", NativeKind.CommanderSetting),
                )
                rankNativeResults(
                    commands + apps + musicResults(player) + screenshotMatches.take(3).map { NativeResult(it.name, "Screenshot · tap to copy", NativeKind.Screenshot, screenshot = it) } +
                        downloadMatches.take(3).map { NativeResult(it.name, "Download · ${it.location}", NativeKind.File, file = it) } +
                        CommandBarHistory.clipboard(engine.context).take(3).map { NativeResult(it.replace('\n', ' ').take(90), "Clipboard · tap to copy", NativeKind.Clipboard, clipboard = it) },
                    "",
                    engine,
                    apps,
                ).take(CommandBarPreferences.resultCount(engine.context))
            }
            mode == SearchMode.Files -> fileMatches.map { NativeResult(it.name, it.location, NativeKind.File, file = it) }
            mode == SearchMode.Settings -> engine.searchSystemSettings(query).map { NativeResult(it.label, it.subtitle, NativeKind.Setting, setting = it) }
            mode == SearchMode.Calculator -> listOfNotNull(engine.calculatorPreview(query)?.let { NativeResult(it, "Calculation", NativeKind.Calculator) })
            mode == SearchMode.Web -> listOf(NativeResult("Search the web for “$query”", kind = NativeKind.Web))
            else -> buildList {
                val menus = listOf(
                    NativeResult("Screenshots", "Browse and copy images", NativeKind.ScreenshotMenu),
                    NativeResult("Music controls", "Playback and open player", NativeKind.MusicMenu),
                    NativeResult("Clipboard history", "Browse copied text", NativeKind.ClipboardMenu),
                    NativeResult("Downloads", "Browse downloaded files", NativeKind.DownloadsMenu),
                    NativeResult("Search Files", "Find device files", NativeKind.BrowseFiles),
                    NativeResult("Search Settings", "Find Android settings", NativeKind.OpenSettings),
                    NativeResult("Calculator", "Calculate or convert", NativeKind.Calculator),
                    NativeResult("Search the web", "Web search", NativeKind.Web),
                    NativeResult("Ask AI", "Prompt ${AiSettings.provider(engine.context).label}", NativeKind.AiMenu),
                    NativeResult("Weather", "Current conditions for a place", NativeKind.Weather),
                    NativeResult("Brightness", "Adjust screen brightness", NativeKind.Brightness),
                    NativeResult("Volume", "Adjust media volume", NativeKind.Volume),
                    NativeResult("Take screenshot", "Save current screen · Shizuku", NativeKind.ScreenshotCapture),
                    NativeResult("Bluetooth", "Turn Bluetooth on or off", NativeKind.BluetoothToggle),
                    NativeResult("Bluetooth devices", "Manage paired devices", NativeKind.BluetoothSettings),
                )
                addAll(menus.filter { it.title.contains(query, true) || it.subtitle.contains(query, true) })
                if (query.startsWith("weather ", true)) add(NativeResult("Weather for ${query.substringAfter(' ')}", "Current conditions", NativeKind.Weather))
                addAll(CommandBarSettingsCatalog.search(query).map { NativeResult(it.title, "Houston settings · ${it.section}", NativeKind.CommanderSetting) })
                if (parseCommand(query).kind != CommandKind.Apps) add(NativeResult("Run command: $query", kind = NativeKind.Command))
                addAll(AppUsageRanker.ranked(engine.context, engine.searchApps(query, 20, includeWebFallback = false)).map { NativeResult(it.label, "App", NativeKind.App, app = it) })
                if (CommandBarPreferences.searchScreenshots(engine.context) && CommandBarDeviceActions.imagePermissionGranted(engine.context)) addAll(screenshotMatches.filter { it.name.contains(query, true) }.take(5).map {
                    NativeResult(it.name, "Screenshot · tap to copy", NativeKind.Screenshot, screenshot = it)
                })
                addAll(musicResults(player).filter { item ->
                    val term = query.trim().lowercase()
                    when (item.kind) {
                        NativeKind.MusicNext -> term in listOf("next", "next track", "skip", "skip track") || item.title.contains(term, true) && term.length > 3
                        NativeKind.MusicPrevious -> term in listOf("previous", "previous track", "back track") || item.title.contains(term, true) && term.length > 3
                        NativeKind.MusicToggle -> term in listOf("play", "pause", "play music", "pause music", "resume") || item.title.contains(term, true) && term.length > 3
                        NativeKind.MusicOpen -> term in listOf("open player", "player", "music app") || item.title.contains(term, true) && term.length > 3
                        else -> false
                    }
                })
                if ("clipboard".contains(query, true)) add(NativeResult("Clipboard history", "Browse copied text", NativeKind.ClipboardMenu))
                if (CommandBarPreferences.showHistory(engine.context)) {
                    addAll(CommandBarHistory.clipboard(engine.context).filter { it.contains(query, true) }.take(4).map {
                        NativeResult(it.replace('\n', ' ').take(90), "Clipboard · tap to copy", NativeKind.Clipboard, clipboard = it)
                    })
                    addAll(CommandBarHistory.searches(engine.context).filter { it.contains(query, true) && !it.equals(query, true) }.take(2).map {
                        NativeResult(it, "Search again", NativeKind.SearchHistory)
                    })
                }
                addAll(DeviceControls.bluetoothDevices(engine.context)
                    .filter { it.name.contains(query, true) || query.contains("bluetooth", true) }
                    .take(8).map { NativeResult(it.name, if (it.connected) "Connected Bluetooth audio · open settings" else "Paired Bluetooth device · open settings", NativeKind.BluetoothDevice) })
                addAll(downloadMatches.take(5).map { NativeResult(it.name, "Download · ${it.location}", NativeKind.File, file = it) })
                addAll(fileMatches.take(4).map { NativeResult(it.name, it.location, NativeKind.File, file = it) })
                addAll(engine.searchSystemSettings(query).take(4).map { NativeResult(it.label, it.subtitle, NativeKind.Setting, setting = it) })
                engine.calculatorPreview(query)?.let { add(NativeResult(it, "Calculation", NativeKind.Calculator)) }
                add(NativeResult("Search the web for “$query”", kind = NativeKind.Web))
            }.distinctBy { it.kind.name + ":" + (it.rankIdentity()) }
                .let { unranked -> rankNativeResults(unranked, query, engine) }
                .take(CommandBarPreferences.resultCount(engine.context))
        }
            // Unified ranking above is already deterministic and history-aware for
            // every mode. Do not re-sort scoped modes by history alone: that would
            // discard typo-tolerant text tiers and hide strong new matches.
    }
    fun resetToAll() {
        control = null
        aiMessages = emptyList()
        aiChatId = null
        showAiHistory = true
        mode = SearchMode.All
        editor = TextFieldValue(sentinel, TextRange(1))
    }
    fun goBack(): Boolean {
        if (mode == SearchMode.All) return false
        resetToAll()
        return true
    }
    fun activate(index: Int) {
        if (mode == SearchMode.Control) {
            val kind = control ?: return
            val percent = ((index + 1).coerceIn(1, 10) * 10)
            controlError = DeviceControls.setLevel(engine.context, kind, percent)
            controlPercent = DeviceControls.level(engine.context, kind)
            return
        }
        val result = results.getOrNull(index) ?: return
        CommandBarRanker.record(engine.context, result.kind.name, result.rankIdentity())
        if (result.kind == NativeKind.Brightness || result.kind == NativeKind.Volume) {
            val level = if (result.kind == NativeKind.Brightness) DeviceControls.Level.Brightness else DeviceControls.Level.Volume
            control = level
            controlPercent = DeviceControls.level(engine.context, level)
            controlError = null
            mode = SearchMode.Control
            editor = TextFieldValue(sentinel, TextRange(1))
            return
        }
        if (result.kind == NativeKind.AiPrompt || (result.kind == NativeKind.Command && parseCommand(query).kind == CommandKind.Ask)) {
            val prompt = (if (result.kind == NativeKind.AiPrompt) query else parseCommand(query).text).trim()
            if (prompt.isNotBlank()) {
                aiMessages = aiMessages + AskMessage("user", prompt)
                aiChatId = AiChatHistory.save(engine.context, aiChatId, aiMessages)
                savedAiChats = AiChatHistory.all(engine.context)
                showAiHistory = false
                aiBusy = true; aiError = null
                mode = SearchMode.Ai
                editor = TextFieldValue(sentinel, TextRange(1))
            }
            return
        }
        if (result.kind == NativeKind.Weather) {
            val place = query.replace(Regex("^weather\\s*(in\\s+)?", RegexOption.IGNORE_CASE), "").trim()
            mode = SearchMode.Weather
            editor = TextFieldValue(sentinel + place, TextRange(sentinel.length + place.length))
            return
        }
        if (result.kind == NativeKind.SearchHistory) {
            editor = TextFieldValue(sentinel + result.title, TextRange(sentinel.length + result.title.length))
            mode = SearchMode.All
            return
        }
        when (result.kind) {
            NativeKind.DownloadsMenu -> { mode = SearchMode.Downloads; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.ScreenshotMenu -> { mode = SearchMode.Screenshots; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.ClipboardMenu -> { CommandBarHistory.recordClipboard(engine.context); mode = SearchMode.Clipboard; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.MusicMenu -> { mode = SearchMode.Music; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.AiMenu -> { mode = SearchMode.Ai; savedAiChats = AiChatHistory.all(engine.context); showAiHistory = true; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.BluetoothSettings -> { mode = SearchMode.Bluetooth; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.BrowseFiles -> { mode = SearchMode.Files; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.OpenSettings -> { mode = SearchMode.Settings; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.Calculator -> if (result.title == "Calculator") { mode = SearchMode.Calculator; editor = TextFieldValue(sentinel, TextRange(1)); return }
            NativeKind.Web -> if (result.title == "Search the web") { mode = SearchMode.Web; editor = TextFieldValue(sentinel, TextRange(1)); return }
            else -> Unit
        }
        if (query.isBlank()) when (result.kind) {
            NativeKind.BrowseFiles -> { mode = SearchMode.Files; return }
            NativeKind.OpenSettings -> { mode = SearchMode.Settings; return }
            NativeKind.Calculator -> { mode = SearchMode.Calculator; return }
            NativeKind.Web -> { mode = SearchMode.Web; return }
            else -> Unit
        }
        onOpen(result, query)
    }
    fun move(delta: Int) {
        if (results.isNotEmpty()) selectedIndex = (selectedIndex + delta).coerceIn(0, results.lastIndex)
    }
    LaunchedEffect(selectedIndex, results.size) {
        if (results.isNotEmpty()) listState.animateScrollToItem(selectedIndex.coerceAtMost(results.lastIndex) + if (mode == SearchMode.All) 0 else 1)
    }
    BackHandler(enabled = true) { if (mode != SearchMode.All) goBack() else closeAnimated() }
    SideEffect {
        onBindings(NativeKeyBindings(::activate, ::move, { activate(selectedIndex) }, {
            if (query.isNotEmpty()) false
            else if (mode == SearchMode.All) { closeAnimated(); true }
            else goBack()
        }, mode == SearchMode.Ai))
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(onClick = ::closeAnimated).imePadding().statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val totalMaxHeight = minOf(screenHeight * (CommandBarPreferences.heightPercent(engine.context) / 100f), (maxHeight - 48.dp).coerceAtLeast(1.dp))
        val miniHeight = if (CommandBarPreferences.miniPlayer(engine.context) && player != null) 72.dp else 0.dp
        val cardMaxHeight = (totalMaxHeight - miniHeight).coerceAtLeast(1.dp)
        val baseSurface = if (CommandBarPreferences.materialYouBar(engine.context) && Build.VERSION.SDK_INT >= 31)
            androidx.compose.material3.dynamicDarkColorScheme(engine.context).surface else Color(0xFF1A1B1E)
        val surface = if (CommandBarPreferences.solid(engine.context)) baseSurface else baseSurface.copy(alpha = .85f)
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 48.dp)
            .heightIn(max = totalMaxHeight)
            .graphicsLayer {
                translationY = entry.value * (size.height + 48.dp.toPx())
                alpha = opacity.value
                scaleY = 1f - kotlin.math.abs(entry.value) * .025f
            }) {
            if (CommandBarPreferences.miniPlayer(engine.context) && player != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .background(surface, RoundedCornerShape(17.dp))
                    .border(1.dp, Color(0x555C5D62), RoundedCornerShape(17.dp))
                    .clickable { onOpen(NativeResult("Open player", kind = NativeKind.MusicOpen), query) }
                    .padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (player?.artwork != null) {
                        val artwork = player?.artwork
                        if (artwork != null) Image(artwork.asImageBitmap(), "Album cover", Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
                        else Icon(Icons.Outlined.MusicNote, null, tint = accentColor(engine.context))
                    }
                    else Icon(Icons.Outlined.MusicNote, null, tint = accentColor(engine.context))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(player?.title.orEmpty().ifBlank { "Music playing" }, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(player?.artist.orEmpty(), color = Color(0xFFAAA8B1), fontSize = 11.sp, maxLines = 1)
                    }
                    Icon(Icons.Outlined.SkipPrevious, "Previous", tint = Color.White,
                        modifier = Modifier.clickable { CommandBarDeviceActions.mediaAction(engine.context, "previous"); player = CommandBarDeviceActions.player(engine.context) }.padding(5.dp))
                    Icon(if (player?.playing == true) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (player?.playing == true) "Pause" else "Play", tint = Color.White,
                        modifier = Modifier.clickable { CommandBarDeviceActions.mediaAction(engine.context, "toggle"); player = CommandBarDeviceActions.player(engine.context) }.padding(5.dp))
                    Icon(Icons.Outlined.SkipNext, "Next", tint = Color.White,
                        modifier = Modifier.clickable { CommandBarDeviceActions.mediaAction(engine.context, "next"); player = CommandBarDeviceActions.player(engine.context) }.padding(5.dp))
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = cardMaxHeight)
                .background(surface, RoundedCornerShape(25.dp))
                .border(1.dp, Color(0x555C5D62), RoundedCornerShape(25.dp))
                .clickable {}.padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(50.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = Color(0xFFECEDEF), modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(
                        when (mode) {
                            SearchMode.All -> "Search for apps and commands…"
                            SearchMode.Files -> "Search files…"
                            SearchMode.Settings -> "Search settings…"
                            SearchMode.Calculator -> "Enter a calculation…"
                            SearchMode.Web -> "Search the web…"
                            SearchMode.Weather -> "Weather for a city…"
                            SearchMode.Screenshots -> "Search screenshots…"
                            SearchMode.Music -> "Search music controls…"
                            SearchMode.Clipboard -> "Search clipboard history…"
                            SearchMode.Downloads -> "Search Downloads…"
                            SearchMode.Ai -> "Ask ${AiSettings.provider(engine.context).label}…"
                            SearchMode.Control -> "Choose a level…"
                            SearchMode.Bluetooth -> "Search Bluetooth devices…"
                        }, color = Color(0xFF93959C), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    BasicTextField(editor, { next ->
                        if (mode == SearchMode.All && query.isBlank() && next.text.length < editor.text.length) {
                            closeAnimated()
                            return@BasicTextField
                        }
                        val typed = next.text.removePrefix(sentinel)
                        if (typed.length == query.length + 1 && typed.startsWith(query) && typed.last() in '0'..'9' &&
                            mode != SearchMode.Calculator && mode != SearchMode.Web && mode != SearchMode.Weather && mode != SearchMode.Ai
                        ) {
                            activate(if (typed.last() == '0') 9 else typed.last() - '1')
                        } else if (mode != SearchMode.All && typed.isEmpty() && query.isEmpty() && next.text.length < editor.text.length) {
                            resetToAll()
                        } else {
                            editor = if (next.text.startsWith(sentinel)) next else TextFieldValue(
                                sentinel + next.text,
                                TextRange((next.selection.start + 1).coerceAtMost(next.text.length + 1)),
                            )
                        }
                    }, Modifier.fillMaxWidth().focusRequester(focus),
                        singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor(engine.context)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { activate(selectedIndex) }),
                    )
                }
                if (mode != SearchMode.All) {
                    Icon(Icons.Outlined.Backspace, "Backspace or return to search", tint = Color(0xFFB9A7FF),
                        modifier = Modifier.size(22.dp).clickable {
                            if (query.isNotEmpty()) {
                                val shorter = query.dropLast(1)
                                editor = TextFieldValue(sentinel + shorter, TextRange(sentinel.length + shorter.length))
                            } else goBack()
                        })
                }
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0x335B5B5F)))
            if (mode == SearchMode.Control && control != null) {
                val activeControl = control
                if (activeControl != null) {
                Text("${activeControl.name} · $controlPercent%", color = Color.White, modifier = Modifier.padding(12.dp))
                Slider(value = controlPercent.toFloat(), onValueChange = {
                    controlPercent = it.toInt()
                    controlError = DeviceControls.setLevel(engine.context, activeControl, controlPercent)
                }, valueRange = 0f..100f, modifier = Modifier.padding(horizontal = 14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..9).forEach { digit ->
                        Text("$digit", color = accentColor(engine.context), modifier = Modifier.clickable {
                            controlPercent = digit * 10
                            controlError = DeviceControls.setLevel(engine.context, activeControl, controlPercent)
                        }.padding(7.dp))
                    }
                }
                }
                controlError?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(12.dp)) }
            } else if (mode == SearchMode.Weather) {
                Text(weatherText, color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp))
            } else if (mode == SearchMode.Ai) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = (cardMaxHeight - 65.dp).coerceAtLeast(1.dp)), state = aiListState, contentPadding = PaddingValues(top = 8.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("New chat", color = accentColor(engine.context), fontSize = 12.sp,
                                modifier = Modifier.clickable { aiChatId = null; aiMessages = emptyList(); aiError = null; showAiHistory = false }.padding(5.dp))
                            Text("History", color = accentColor(engine.context), fontSize = 12.sp,
                                modifier = Modifier.clickable { savedAiChats = AiChatHistory.all(engine.context); showAiHistory = !showAiHistory }.padding(5.dp))
                        }
                    }
                    if (showAiHistory) itemsIndexed(savedAiChats) { _, chat ->
                        Row(Modifier.fillMaxWidth().clickable {
                            aiChatId = chat.id; aiMessages = chat.messages; aiError = null; showAiHistory = false
                        }.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(chat.title, color = Color.White, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.DeleteOutline, "Delete chat", tint = Color(0xFFBDBDC4), modifier = Modifier.size(22.dp).clickable {
                                AiChatHistory.delete(engine.context, chat.id)
                                savedAiChats = AiChatHistory.all(engine.context)
                                if (aiChatId == chat.id) { aiChatId = null; aiMessages = emptyList() }
                            }.padding(3.dp))
                        }
                    }
                    if (!showAiHistory) itemsIndexed(aiMessages) { _, message ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(if (message.role == "user") "You" else if (message.role == "tool") "Screenshot" else AiSettings.provider(engine.context).label,
                                color = accentColor(engine.context), fontSize = 12.sp)
                            if (message.role == "assistant") MarkdownText(message.text)
                            else if (message.role != "tool") Text(message.text, color = Color.White, fontSize = 14.sp)
                            message.imagePath?.let { path ->
                                val bitmap by produceState<ImageBitmap?>(null, path) {
                                    value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
                                }
                                bitmap?.let { Image(it, "Screenshot attached to conversation", Modifier.size(110.dp).clip(RoundedCornerShape(10.dp))) }
                            }
                        }
                    }
                    if (!showAiHistory && aiBusy) item { Text(aiToolStatus ?: "Thinking…", color = Color(0xFFBDBDC4), modifier = Modifier.padding(12.dp)) }
                    if (!showAiHistory) aiError?.let { error -> item { Text(error, color = Color(0xFFFFB4AB), modifier = Modifier.padding(12.dp)) } }
                }
            } else if (mode == SearchMode.Screenshots && CommandBarDeviceActions.imagePermissionGranted(engine.context)) {
                LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxWidth().heightIn(max = (cardMaxHeight - 65.dp).coerceAtLeast(1.dp)), contentPadding = PaddingValues(top = 8.dp)) {
                    gridItemsIndexed(results) { index, result ->
                        ScreenshotTile(result, index, selectedIndex == index) { selectedIndex = index; activate(index) }
                    }
                }
            } else LazyColumn(Modifier.fillMaxWidth().heightIn(max = (cardMaxHeight - 65.dp).coerceAtLeast(1.dp)), state = listState, contentPadding = PaddingValues(top = 8.dp)) {
                if (mode != SearchMode.All) item {
                    Text(mode.name, color = Color(0xFF99999E), fontSize = 12.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 13.dp, bottom = 5.dp))
                }
                if (results.isEmpty()) item {
                    Text(
                        if (mode == SearchMode.Screenshots) "No screenshots found on this device"
                        else if (mode == SearchMode.Clipboard) "No saved text clips yet. Copy text, then open Houston to save it."
                        else if (mode == SearchMode.Bluetooth) "No paired or connected Bluetooth devices found"
                        else "Type to search",
                        color = Color(0xFF8E8F95), fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 20.dp),
                    )
                }
                itemsIndexed(results) { index, result ->
                    NativeResultRow(result, index, engine, player, selected = index == selectedIndex,
                        modifier = Modifier.clickable { selectedIndex = index; activate(index) })
                }
            }
            }
        }
    }
}
