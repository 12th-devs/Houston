package com.astroboii47.commander

import android.app.Activity
import android.content.Context

/**
 * Facade preserving the original CommandEngine API.
 * Delegates to FileEngine, ContactEngine, AppSearchEngine, TimerCalcEngine.
 * Holds Activity for compat; new engines hold only applicationContext.
 */
class CommandEngine(private val activity: Activity) {
    private val appContext: Context = activity.applicationContext
    private val fileEngine = FileEngine(appContext)
    private val contactEngine = ContactEngine(appContext)
    private val appEngine = AppSearchEngine(appContext)
    private val timerEngine = TimerCalcEngine(appContext)

    val context: Context get() = activity

    fun warmAppIndex() = appEngine.warmAppIndex()
    fun directTodoistEnabled(): Boolean = appEngine.directTodoistEnabled()
    fun directSmsEnabled(): Boolean = contactEngine.directSmsEnabled()
    fun hasContactsPermission(): Boolean = contactEngine.hasContactsPermission()
    fun requestContactsPermission() = contactEngine.requestContactsPermission(activity)
    fun openAppInfo(app: AppResult): Boolean = appEngine.openAppInfo(activity, app)
    fun hasGeminiKey(): Boolean = appEngine.hasGeminiKey()
    suspend fun askGemini(history: List<AskMessage>): Result<String> = appEngine.askGemini(history)

    fun searchApps(query: String, limit: Int = 7, includeWebFallback: Boolean = true): List<AppResult> =
        appEngine.searchApps(query, limit, includeWebFallback)
    fun allApps(query: String, limit: Int = Int.MAX_VALUE): List<AppResult> = appEngine.allApps(query, limit)
    fun commanderRecents(query: String, limit: Int = 10): List<AppResult> = appEngine.commanderRecents(query, limit)
    fun loadVisibleAppIcons(apps: List<AppResult>): List<AppResult> = appEngine.loadVisibleAppIcons(apps)
    fun webFallbackResult(query: String): AppResult? = appEngine.webFallbackResult(query)
    fun loadPackageIcon(packageName: String) = appEngine.loadPackageIcon(packageName)
    fun loadSystemSettingsIcon() = appEngine.loadSystemSettingsIcon()

    fun searchFiles(query: String, limit: Int = 7, mode: FileSearchMode = FileSearchMode.Default, sortMode: FileSortMode = FileSortMode.Relevance): List<FileResult> =
        fileEngine.searchFiles(query, limit, mode, sortMode)
    fun hasFileSearchAccess(): Boolean = fileEngine.hasFileSearchAccess()
    fun openFileSearchAccess(): String? = fileEngine.openFileSearchAccess(activity)
    fun openFile(file: FileResult): String? = fileEngine.openFile(activity, file)
    fun openFileWith(file: FileResult): String? = fileEngine.openFileWith(activity, file)
    fun revealFile(file: FileResult): String? = fileEngine.revealFile(activity, file)
    fun fileInfo(file: FileResult): String = fileEngine.fileInfo(file)
    fun filePreview(file: FileResult): android.graphics.Bitmap? = fileEngine.filePreview(file)
    fun deleteFile(file: FileResult): String? = fileEngine.deleteFile(file)
    fun loadFilePreviews(files: List<FileResult>): List<FileResult> = fileEngine.loadFilePreviews(files)

    fun matchAlias(input: String): AliasMatch? = appEngine.matchAlias(input)
    fun searchSystemSettings(query: String): List<SystemSettingResult> = appEngine.searchSystemSettings(query)
    fun openSystemSetting(result: SystemSettingResult): String? = appEngine.openSystemSetting(activity, result)
    fun calculatorPreview(text: String): String? = timerEngine.calculatorPreview(text)
    fun openAlias(match: AliasMatch): String? = appEngine.openAlias(activity, match)

    fun searchContacts(
        query: String,
        limit: Int = 5,
        includeMessenger: Boolean = false,
        platform: MessagingPlatformOption? = null,
    ): List<ContactResult> = contactEngine.searchContacts(query, limit, includeMessenger, platform)
    fun sendCommanderMessage(contact: ContactResult, body: String): CommanderMessageDelivery =
        contactEngine.sendCommanderMessage(contact, body)
    fun messageContact(contact: ContactResult, body: String = ""): String? =
        contactEngine.messageContact(activity, contact, body)
    suspend fun sendMessageContact(contact: ContactResult, body: String): String? =
        contactEngine.sendMessageContact(contact, body)
    fun callContact(contact: ContactResult): String? = contactEngine.callContact(activity, contact)

    fun openCamera(): String? = appEngine.openCamera(activity)
    fun openTimer(): String? = timerEngine.openTimer(activity)
    fun openMemo(): String? = appEngine.openMemo(activity)
    fun openChatGpt(): String? = appEngine.openChatGpt(activity)

    fun alarmTimeLabel(text: String): String? = timerEngine.alarmTimeLabel(text)
    fun timerInputPreview(text: String): String? = timerEngine.timerInputPreview(text)

    suspend fun execute(command: ParsedCommand, firstApp: AppResult?): String? = when (command.kind) {
        CommandKind.Apps -> if (firstApp == null) "No matching app" else appEngine.launchApp(activity, firstApp)
        CommandKind.Files -> "Choose a file result"
        CommandKind.Todo -> appEngine.executeTodo(activity, command.text)
        CommandKind.Event -> appEngine.executeEvent(activity, command.text)
        CommandKind.Timer -> timerEngine.setTimer(activity, command.text)
        CommandKind.Calculator -> timerEngine.calculate(command.text)
        CommandKind.Web -> appEngine.executeWeb(activity, command.text)
        CommandKind.Note -> appEngine.executeNote(activity, command.text)
        CommandKind.Call -> contactEngine.executeCall(activity, command.text)
        CommandKind.Message -> contactEngine.executeMessage(activity, command.text)
        CommandKind.Ask -> if (command.text.startsWith('?')) appEngine.executeAsk(activity, command.text) else "Ask is handled in Command"
    }
}
