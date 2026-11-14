package com.astroboii47.commander

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.provider.OpenableColumns
import android.os.StatFs

class SettingsActivity : ComponentActivity() {
    private var revision by mutableIntStateOf(0)
    private var modelImportStatus by mutableStateOf("")
    private var modelImportProgress by mutableStateOf<Float?>(null)
    private var modelImporting by mutableStateOf(false)
    private val importLiteRtModel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || modelImporting) return@registerForActivityResult
        modelImporting = true
        modelImportProgress = null
        modelImportStatus = "Copying model to Houston…"
        lifecycleScope.launch(Dispatchers.IO) {
            val target = filesDir.resolve("gemma4.litertlm")
            val temporary = filesDir.resolve("gemma4.importing")
            val outcome = runCatching {
                val size = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                } ?: 0L
                require(size <= 0 || size + 100_000_000L < StatFs(filesDir.absolutePath).availableBytes) { "Not enough free space to import this model" }
                var copied = 0L
                contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var lastUpdate = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (copied - lastUpdate >= 8L * 1024 * 1024 || (size > 0 && copied >= size)) {
                                lastUpdate = copied
                                val progress = if (size > 0) (copied.toFloat() / size).coerceIn(0f, 1f) else null
                                val copiedMb = copied / (1024 * 1024)
                                val total = if (size > 0) " / ${size / (1024 * 1024)} MB" else " MB"
                                withContext(Dispatchers.Main) {
                                    modelImportProgress = progress
                                    modelImportStatus = "Copying model: $copiedMb$total"
                                }
                            }
                        }
                    }
                } ?: error("Could not read model")
                require(size <= 0 || copied == size) { "The model copy is incomplete. Download the file again and retry." }
                LiteRtAi.validateModel(temporary)
                require(temporary.renameTo(target)) { "Could not save the model" }
                AiSettings.setLiteRtModelPath(this@SettingsActivity, target.absolutePath)
                "Gemma 4 model ready"
            }.getOrElse { temporary.delete(); it.message ?: "Model import failed" }
            withContext(Dispatchers.Main) {
                modelImportStatus = outcome
                modelImportProgress = null
                modelImporting = false
                revision++
            }
        }
    }
    private val assistantRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        AssistantLog.i(
            "Role request returned resultCode=${result.resultCode} nowDefault=${AssistantManager.isDefault(this)}",
        )
        revision++
    }
    private val imagePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { revision++ }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SettingsScreen(revision, intent.getStringExtra("focus")) }
    }
    override fun onResume() {
        super.onResume()
        // Re-attach a killed handle whenever returning here with it enabled
        // (service death, revoked-then-regranted overlay permission, etc).
        EdgeBarSettings.ensureStarted(this)
        revision++
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun SettingsScreen(@Suppress("UNUSED_PARAMETER") revision: Int, focus: String?) {
        val context = LocalContext.current
        var theme by remember { mutableIntStateOf(CommandBarPreferences.theme(context)) }
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        val dark = when (theme) { 1 -> false; 2, 3 -> systemDark; else -> true }
        val colors = if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB9A7FF))
            else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6650A4))
        MaterialTheme(colorScheme = colors) {
            // Draw behind transparent system bars (enableEdgeToEdge) and keep their
            // icons readable against the current theme instead of the fixed theme default.
            SideEffect {
                val activity = context as? android.app.Activity ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            var edge by remember { mutableStateOf(EdgeBarSettings.enabled(context)) }
            var right by remember { mutableStateOf(EdgeBarSettings.rightSide(context)) }
            var size by remember { mutableIntStateOf(EdgeBarSettings.handleSize(context)) }
            var distance by remember { mutableFloatStateOf(EdgeBarSettings.swipeDistance(context).toFloat()) }
            var solid by remember { mutableStateOf(CommandBarPreferences.solid(context)) }
            var mini by remember { mutableStateOf(CommandBarPreferences.miniPlayer(context)) }
            var compact by remember { mutableStateOf(CommandBarPreferences.compact(context)) }
            var accent by remember { mutableIntStateOf(CommandBarPreferences.accent(context)) }
            var resultCount by remember { mutableFloatStateOf(CommandBarPreferences.resultCount(context).toFloat()) }
            var keyboard by remember { mutableStateOf(CommandBarPreferences.showKeyboard(context)) }
            var screenshots by remember { mutableStateOf(CommandBarPreferences.searchScreenshots(context)) }
            var files by remember { mutableStateOf(CommandBarPreferences.searchFiles(context)) }
            var history by remember { mutableStateOf(CommandBarPreferences.showHistory(context)) }
            var notificationSearch by remember { mutableStateOf(NotificationSearchSettings.enabled(context)) }
            var barMaterial by remember { mutableStateOf(CommandBarPreferences.materialYouBar(context)) }
            var barHeight by remember { mutableFloatStateOf(CommandBarPreferences.heightPercent(context).toFloat()) }
            var aiProvider by remember { mutableStateOf(AiSettings.provider(context)) }
            var aiModel by remember { mutableStateOf(AiSettings.model(context, aiProvider)) }
            var aiKey by remember { mutableStateOf("") }
            var aiTools by remember { mutableStateOf(AiSettings.toolsEnabled(context)) }
            var aiSystemPrompt by remember { mutableStateOf(AiSettings.systemPrompt(context)) }
            var aiToolPrompt by remember { mutableStateOf(AiSettings.toolPrompt(context)) }
            var deviceAiStatus by remember { mutableStateOf("") }
            var deviceAiDownloading by remember { mutableStateOf(false) }
            val aiScope = rememberCoroutineScope()
            LaunchedEffect(aiProvider) {
                if (aiProvider == AiProvider.Device) deviceAiStatus = DeviceAi.status()
            }
            var choice by remember { mutableStateOf<String?>(null) }
            var page by remember { mutableStateOf(if (focus?.contains("AI", true) == true) "ai" else "main") }
            BackHandler(page != "main") { page = "main" }
            val scroll = rememberScrollState()
            LaunchedEffect(page) { scroll.scrollTo(0) }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(if (page == "ai") "AI settings" else "Houston Settings") },
                        navigationIcon = {
                            if (page != "main") IconButton(onClick = { page = "main" }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "All settings")
                            }
                        }
                    )
                    Column(
                        Modifier.weight(1f).verticalScroll(scroll)
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 18.dp)
                    ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp)) {
                            Icon(if (page == "main") Icons.Outlined.Tune else Icons.Outlined.SmartToy,
                                null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(20.dp))
                            Text(if (page == "main") "Make Houston yours" else "AI settings",
                                style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(if (page == "main") "Shape how you search, open and stay in touch." else "Choose a model and what it can do.",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    if (!focus.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("From search: $focus", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(20.dp))
                    if (page == "main") {
                    Section("Access")
                    ToggleSetting("Edge handle", "Swipe inward to open Houston", edge) {
                        if (it && !Settings.canDrawOverlays(this@SettingsActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                            Toast.makeText(this@SettingsActivity, "Allow display over other apps, then return to enable", Toast.LENGTH_LONG).show()
                        } else { edge = it; EdgeBarSettings.setEnabled(this@SettingsActivity, it) }
                    }
                    var edgeProbe by remember { mutableIntStateOf(0) }
                    ChoiceSetting("Edge handle status", run { edgeProbe; when {
                        !edge -> "Off"
                        !Settings.canDrawOverlays(context) -> "Needs display over other apps permission"
                        EdgeBarService.running -> "Running"
                        else -> "Enabled · tap to restart"
                    } }) {
                        if (!edge) {
                            if (!Settings.canDrawOverlays(this@SettingsActivity)) {
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                Toast.makeText(this@SettingsActivity, "Allow display over other apps, then return to enable", Toast.LENGTH_LONG).show()
                            } else { edge = true; EdgeBarSettings.setEnabled(this@SettingsActivity, true) }
                        } else if (!Settings.canDrawOverlays(context)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } else {
                            EdgeBarSettings.refresh(this@SettingsActivity)
                            Toast.makeText(context, "Edge handle restarted", Toast.LENGTH_SHORT).show()
                            edgeProbe++
                        }
                    }
                    ChoiceSetting("Handle side", if (right) "Right" else "Left") { choice = "side" }
                    ChoiceSetting("Handle size", listOf("Small", "Medium", "Large")[size]) { choice = "size" }
                    SliderSetting("Swipe distance", "${distance.toInt()} dp · shorter is more sensitive", distance, 12f..60f, 12) {
                        distance = it; EdgeBarSettings.setSwipeDistance(context, it.toInt())
                    }
                    ChoiceSetting("App usage access", if (AppUsageRanker.hasAccess(context)) "Granted" else "Grant to rank apps by use") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    ChoiceSetting("Screenshot access", if (CommandBarDeviceActions.imagePermissionGranted(context)) "Granted" else "Grant photo access") { imagePermissions.launch(CommandBarDeviceActions.imagePermissions()) }
                    ChoiceSetting("Screenshot capture", if (HomeTypingAccessibilityService.isConnected()) "Ready" else "Enable Houston accessibility service") {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    ChoiceSetting("Bluetooth device access", if (DeviceControls.bluetoothPermission(context)) "Granted" else "Show paired Bluetooth devices") {
                        if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    ChoiceSetting("Notification access", if (NotificationSearchSettings.hasAccess(context)) "Granted · player controls and live notification search" else "Grant for player controls and live notification search") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    ChoiceSetting("File access", if (CommandEngine(this@SettingsActivity).hasFileSearchAccess()) "Granted" else "Needed to search downloads and files") { CommandEngine(this@SettingsActivity).openFileSearchAccess() }
                    Section("Assistant")
                    var assistantProbe by remember { mutableIntStateOf(0) }
                    var shareScreen by remember { mutableStateOf(AssistantConsent.isScreenSharingAllowed(context)) }
                    var voiceInput by remember { mutableStateOf(AssistantConsent.isVoiceEnabled(context)) }
                    ChoiceSetting("Default assistant", run { assistantProbe; AssistantManager.statusLabel(context) }) {
                        assistantProbe++
                        if (!AssistantManager.requestRole(this@SettingsActivity, assistantRoleLauncher)) {
                            AssistantManager.openAssistantSettings(context)
                            Toast.makeText(context, "Choose Houston as the assist app", Toast.LENGTH_LONG).show()
                        }
                    }
                    ChoiceSetting("Assistant system settings", "Manual selection and gesture options") {
                        AssistantManager.openAssistantSettings(context)
                    }
                    ChoiceSetting("View assistant logs", run { assistantProbe; "${AssistantLog.entries().size} entries · tap to read and copy" }) {
                        assistantProbe++
                        startActivity(Intent(context, AssistantLogActivity::class.java))
                    }
                    var lastDiagnosis by remember { mutableStateOf<String?>(null) }
                    ChoiceSetting(
                        "Run assistant diagnostics",
                        run { assistantProbe; lastDiagnosis ?: "Find out why the system never calls Houston" },
                    ) {
                        val diagnosis = AssistantManager.diagnose(context)
                        lastDiagnosis = diagnosis.summary()
                        assistantProbe++
                        Toast.makeText(context, diagnosis.summary(), Toast.LENGTH_LONG).show()
                    }
                    ToggleSetting("Share screen context", "Let the assistant use the current screen when you ask about it", shareScreen) {
                        shareScreen = it; AssistantConsent.setScreenSharingAllowed(context, it)
                    }
                    ToggleSetting("Voice input", "Microphone button in the command bar", voiceInput) {
                        voiceInput = it; AssistantConsent.setVoiceEnabled(context, it)
                    }
                    Text("Set Houston as the default to invoke it with the system assistant gesture or button. Selection stays under your control in Android settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    Section("Appearance")
                    ChoiceSetting("Theme", listOf("Dark", "Light", "System", "Material You")[theme]) { choice = "theme" }
                    ToggleSetting("Material You command bar", "Use wallpaper colors for the bar and its icons", barMaterial) {
                        barMaterial = it; CommandBarPreferences.setMaterialYouBar(context, it)
                    }
                    SliderSetting("Command bar height", "${barHeight.toInt()}% of the screen", barHeight, 22f..70f, 23) {
                        barHeight = it; CommandBarPreferences.setHeightPercent(context, it.toInt())
                    }
                    ChoiceSetting("Surface style", if (solid) "Solid" else "Frosted") { choice = "surface" }
                    ToggleSetting("Music mini player", "Separate player above the command bar", mini) { mini = it; CommandBarPreferences.setMiniPlayer(context, it) }
                    ToggleSetting("Compact rows", "Fit more results on screen", compact) { compact = it; CommandBarPreferences.setCompact(context, it) }
                    ChoiceSetting("Accent color", if (theme == 3) "Material You" else listOf("Violet", "Cyan", "Amber", "Mint")[accent]) { choice = "accent" }
                    SliderSetting("Visible results", "${resultCount.toInt()} results", resultCount, 6f..20f, 14) {
                        resultCount = it; CommandBarPreferences.setResultCount(context, it.toInt())
                    }
                    ToggleSetting("Show keyboard", "Open the keyboard when Houston appears", keyboard) { keyboard = it; CommandBarPreferences.setShowKeyboard(context, it) }
                    Section("Search")
                    ToggleSetting("Search screenshots", "Include screenshots in main search", screenshots) { screenshots = it; CommandBarPreferences.setSearchScreenshots(context, it) }
                    ToggleSetting("Search files", "Include files and downloads", files) { files = it; CommandBarPreferences.setSearchFiles(context, it) }
                    ToggleSetting("Search live notifications", "Include active, dismissible notifications; Enter opens their default action", notificationSearch) {
                        notificationSearch = it; NotificationSearchSettings.setEnabled(context, it)
                        if (it && !NotificationSearchSettings.hasAccess(context)) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    ToggleSetting("History results", "Show clipboard and past searches", history) { history = it; CommandBarPreferences.setShowHistory(context, it) }
                    Text("Clipboard history saves the current text clip when Houston opens. Android does not let apps import older system clipboard entries.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    Section("Integrations")
                    ChoiceSetting("AI settings", "Provider, model, and API key") { page = "ai" }
                    Section("App")
                    var showUpdate by remember { mutableStateOf(false) }
                    var updateState by remember { mutableStateOf<UpdateManager.CheckResult?>(null) }
                    var checkingUpdate by remember { mutableStateOf(false) }
                    var updProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
                    var downloading by remember { mutableStateOf(false) }
                    val appVersion = remember {
                        try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { null }
                    }
                    ChoiceSetting(
                        "App updates",
                        "v${appVersion ?: "?"} · tap to check GitHub pre-releases",
                    ) { showUpdate = true }
                    if (showUpdate) {
                        AlertDialog(
                            onDismissRequest = { if (!checkingUpdate && !downloading) showUpdate = false },
                            title = { Text("App updates") },
                            text = {
                                Column {
                                    when (val st = updateState) {
                                        null -> if (checkingUpdate) {
                                            Text("Checking…", style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(8.dp))
                                            LinearProgressIndicator(Modifier.fillMaxWidth())
                                        } else {
                                            Text(
                                                "Houston checks GitHub pre-releases and installs them over this app — same signature, your data stays.",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        is UpdateManager.CheckResult.UpToDate ->
                                            Text("You're on the latest release.", style = MaterialTheme.typography.bodyMedium)
                                        is UpdateManager.CheckResult.Failed ->
                                            Text(st.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                        is UpdateManager.CheckResult.Available -> {
                                            Text("v${st.info.versionName} is ready (${st.info.tag}).", style = MaterialTheme.typography.titleSmall)
                                            if (st.info.notes.isNotBlank()) {
                                                Text(st.info.notes.take(500), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                            }
                                            val (done, total) = updProgress ?: Pair(0L, -1L)
                                            if (downloading) {
                                                Spacer(Modifier.height(8.dp))
                                                if (total > 0) {
                                                    LinearProgressIndicator(progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                                    Text("${done / 1_048_576} / ${total / 1_048_576} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else {
                                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                                    Text("Downloading… ${done / 1_048_576} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                val st = updateState
                                when {
                                    checkingUpdate || downloading -> {}
                                    st is UpdateManager.CheckResult.Available && st.downloaded != null -> {
                                        TextButton(onClick = {
                                            runCatching { UpdateManager.install(this@SettingsActivity, st.downloaded) }.onFailure {
                                                Toast.makeText(this@SettingsActivity, "Couldn't open installer: ${it.message?.take(120)}", Toast.LENGTH_LONG).show()
                                            }
                                            showUpdate = false
                                        }) { Text("Install") }
                                    }
                                    st is UpdateManager.CheckResult.Available -> {
                                        TextButton(onClick = {
                                            val info = st.info
                                            downloading = true
                                            updProgress = Pair(0L, -1L)
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                try {
                                                    val apk = UpdateManager.download(this@SettingsActivity, info) { done, total ->
                                                        lifecycleScope.launch(Dispatchers.Main) { updProgress = Pair(done, total) }
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        updateState = UpdateManager.CheckResult.Available(info, apk)
                                                        if (!UpdateManager.canInstall(this@SettingsActivity)) {
                                                            Toast.makeText(this@SettingsActivity, "Allow Houston to install apps, then tap Install.", Toast.LENGTH_LONG).show()
                                                            UpdateManager.openUnknownSourcesSettings(this@SettingsActivity)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        updateState = UpdateManager.CheckResult.Failed((e.message ?: "Download failed.").take(200))
                                                    }
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        downloading = false
                                                        updProgress = null
                                                    }
                                                }
                                            }
                                        }) { Text("Download") }
                                    }
                                    else -> {
                                        TextButton(onClick = {
                                            checkingUpdate = true
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                val result = UpdateManager.check(this@SettingsActivity)
                                                withContext(Dispatchers.Main) {
                                                    updateState = result
                                                    checkingUpdate = false
                                                    if (result is UpdateManager.CheckResult.Failed) {
                                                        Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }) { Text("Check now") }
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { if (!checkingUpdate && !downloading) showUpdate = false }) { Text("Close") }
                            },
                        )
                    }
                    Section("Privacy")
                    ChoiceSetting("Clear clipboard history", "Delete text saved by Houston") { CommandBarHistory.clearClipboard(context); Toast.makeText(context, "Clipboard history cleared", Toast.LENGTH_SHORT).show() }
                    ChoiceSetting("Clear search history and ranking", "Reset learned results") { CommandBarHistory.clearSearches(context); CommandBarRanker.clear(context); Toast.makeText(context, "Search history and ranking cleared", Toast.LENGTH_SHORT).show() }
                    Section("Command symbols")
                    Text("Start a query with a symbol to run a focused command. Number keys 1–9 and 0 activate visible results immediately.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    listOf(
                        "!" to "Create a note", "#" to "Call a contact", "@" to "Send a message",
                        "/" to "Search the web", "?" to "Ask the configured AI", "-" to "Add a to-do",
                        "+" to "Set a timer", "*" to "Create a calendar event", "," to "Calculate or convert",
                    ).forEach { (symbol, description) -> SymbolRow(symbol, description) }
                    Text("A leading space searches files. Some commands need their app or permission configured.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    }
                    if (page == "ai") {
                    SubHeader("Model")
                    ChoiceSetting("AI provider", aiProvider.label) { choice = "ai_provider" }
                    ToggleSetting("AI tools", "Allow your model to use web search, weather, date, files, screenshots, Bluetooth, volume, brightness, and music controls", aiTools) {
                        aiTools = it; AiSettings.setToolsEnabled(context, it)
                    }
                    SubHeader("Prompts")
                    OutlinedTextField(aiSystemPrompt, { aiSystemPrompt = it }, label = { Text("System prompt") },
                        supportingText = { Text("Instructions sent with every AI request") }, minLines = 4,
                        modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { AiSettings.setSystemPrompt(context, aiSystemPrompt); Toast.makeText(context, "System prompt saved", Toast.LENGTH_SHORT).show() }) { Text("Save prompt") }
                        TextButton(onClick = { AiSettings.resetSystemPrompt(context); aiSystemPrompt = AiSettings.DEFAULT_SYSTEM_PROMPT }) { Text("Reset to default") }
                    }
                    if (aiTools && aiProvider != AiProvider.LiteRt) {
                        OutlinedTextField(aiToolPrompt, { aiToolPrompt = it }, label = { Text("Tool instructions") },
                            supportingText = { Text("Tool format and available actions sent to the model") }, minLines = 6,
                            modifier = Modifier.fillMaxWidth())
                        Row {
                            TextButton(onClick = { AiSettings.setToolPrompt(context, aiToolPrompt); Toast.makeText(context, "Tool instructions saved", Toast.LENGTH_SHORT).show() }) { Text("Save tools prompt") }
                            TextButton(onClick = { AiSettings.resetToolPrompt(context); aiToolPrompt = AiTools.instructions }) { Text("Reset to default") }
                        }
                    }
                    Text("When enabled, relevant tool results can be sent to the selected AI provider. Device actions may change settings immediately. Chats are saved privately on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    SubHeader(when (aiProvider) {
                        AiProvider.Device -> "On-device model"
                        AiProvider.LiteRt -> "Local model file"
                        else -> "Cloud model"
                    })
                    if (aiProvider == AiProvider.Device) {
                        Text("The installed AICore model runs locally. Image input is sent when a screenshot is attached and the device model supports it. No API key is needed.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                        Text(deviceAiStatus, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
                        if (deviceAiStatus.contains("download needed", true) || deviceAiDownloading) {
                            TextButton(onClick = {
                                deviceAiDownloading = true
                                aiScope.launch {
                                    val result = DeviceAi.download()
                                    deviceAiStatus = if (result.isSuccess) DeviceAi.status() else result.exceptionOrNull()?.message.orEmpty()
                                    deviceAiDownloading = false
                                }
                            }, enabled = !deviceAiDownloading) { Text(if (deviceAiDownloading) "Downloading…" else "Download model") }
                        }
                    } else if (aiProvider == AiProvider.LiteRt) {
                        Text("LiteRT-LM uses a local Gemma 4 E2B or E4B .litertlm model. Download the complete CPU model file (gemma-4-E2B-it.litertlm, about 2.6 GB), then import that file. Native tool calling uses Houston’s existing actions. AICore remains available as a separate provider.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                        ChoiceSetting("Import Gemma 4 model", modelImportStatus.ifBlank {
                            if (java.io.File(AiSettings.liteRtModelPath(context)).isFile) "Model ready" else "Choose a .litertlm file"
                        }) { if (!modelImporting) importLiteRtModel.launch(arrayOf("application/octet-stream", "*/*")) }
                        if (modelImporting) {
                            val progress = modelImportProgress
                            if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"))) }) {
                            Text("Get the official LiteRT-LM E2B model")
                        }
                    } else {
                    Text("Choose Gemini, OpenRouter, OpenAI, or Anthropic. Enter any model ID supported by that provider.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                    OutlinedTextField(aiModel, { aiModel = it }, label = { Text("AI model") },
                        supportingText = { Text("Use auto for Gemini model discovery") },
                        modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { AiSettings.setModel(context, aiProvider, aiModel.trim()); Toast.makeText(context, "AI model saved", Toast.LENGTH_SHORT).show() }) { Text("Save model") }
                    OutlinedTextField(aiKey, { aiKey = it }, label = { Text("${aiProvider.label} API key") },
                        placeholder = { Text(if (AiSettings.apiKey(context, aiProvider).isBlank()) "Enter API key" else "Key saved · enter a replacement") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = {
                            if (aiKey.isNotBlank()) { AiSettings.setApiKey(context, aiProvider, aiKey); aiKey = ""; Toast.makeText(context, "API key saved securely", Toast.LENGTH_SHORT).show() }
                        }, enabled = aiKey.isNotBlank()) { Text("Save key") }
                        TextButton(onClick = { AiSettings.setApiKey(context, aiProvider, ""); aiKey = "" }) { Text("Remove key") }
                    }
                    }
                    }
                    }
                }
            }
            val options = when (choice) {
                "side" -> listOf("Left", "Right")
                "size" -> listOf("Small", "Medium", "Large")
                "theme" -> listOf("Dark", "Light", "System", "Material You")
                "surface" -> listOf("Frosted", "Solid")
                "accent" -> listOf("Violet", "Cyan", "Amber", "Mint")
                "ai_provider" -> AiProvider.entries.map { it.label }
                else -> emptyList()
            }
            val selected = when (choice) {
                "side" -> if (right) 1 else 0
                "size" -> size
                "theme" -> theme
                "surface" -> if (solid) 1 else 0
                "accent" -> accent
                "ai_provider" -> AiProvider.entries.indexOf(aiProvider)
                else -> -1
            }
            if (choice != null) AlertDialog(onDismissRequest = { choice = null }, title = { Text(when (choice) { "side" -> "Handle side"; "size" -> "Handle size"; "theme" -> "Theme"; "surface" -> "Surface style"; "ai_provider" -> "AI provider"; else -> "Accent color" }) },
                text = { Column { options.forEachIndexed { index, option ->
                    Row(Modifier.fillMaxWidth().clickable {
                        when (choice) {
                            "side" -> { right = index == 1; EdgeBarSettings.setRightSide(context, right) }
                            "size" -> { size = index; EdgeBarSettings.setHandleSize(context, size) }
                            "theme" -> { theme = index; CommandBarPreferences.setTheme(context, index) }
                            "surface" -> { solid = index == 1; CommandBarPreferences.setSolid(context, solid) }
                            "accent" -> { accent = index; CommandBarPreferences.setAccent(context, index) }
                            "ai_provider" -> {
                                aiProvider = AiProvider.entries[index]; AiSettings.setProvider(context, aiProvider)
                                aiModel = AiSettings.model(context, aiProvider); aiKey = ""
                            }
                        }
                        choice = null
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == index, onClick = null)
                        Text(option, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp))
                    } }
                } }, confirmButton = { TextButton(onClick = { choice = null }) { Text("Cancel") } })
        }
    }

    @Composable private fun SubHeader(text: String) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
    }
    @Composable private fun Section(title: String) {
        val icon: ImageVector = when (title) {
            "Access" -> Icons.Outlined.Tune
            "Appearance" -> Icons.Outlined.Palette
            "Search" -> Icons.Outlined.Search
            "Integrations" -> Icons.Outlined.Extension
            "Privacy" -> Icons.Outlined.Shield
            else -> Icons.Outlined.Tune
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }
    }
    @Composable private fun SettingCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
        val modifier = if (onClick != null) Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick)
            else Modifier.fillMaxWidth().padding(bottom = 8.dp)
        Card(modifier, shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), content = content)
        }
    }

    @Composable private fun SettingSection(title: String) = Section(title)

    @Composable private fun SettingToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) =
        ToggleSetting(title, subtitle, value, onChange)

    @Composable private fun ToggleSetting(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(value, onCheckedChange = null)
            }
        }
    }
    @Composable private fun ChoiceSetting(title: String, value: String, onClick: () -> Unit) {
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(onClick = onClick), shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    @Composable private fun SliderSetting(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value, onValueChange = onChange, valueRange = range, steps = steps)
            }
        }
    }
    @Composable private fun SymbolRow(symbol: String, description: String) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(40.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
