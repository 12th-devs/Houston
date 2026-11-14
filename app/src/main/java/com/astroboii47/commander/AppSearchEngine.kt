package com.astroboii47.commander

import android.app.Activity
import android.app.ActivityOptions
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App search, recents, icons, aliases, settings shortcuts and external intents.
 * Holds only applicationContext. Activity passed per launch call.
 */
class AppSearchEngine(private val appContext: Context) {

    fun warmAppIndex() {
        AppCatalog.initialize(appContext)
    }

    fun directTodoistEnabled(): Boolean = TodoistSettings.directEnabled(appContext)

    fun hasGeminiKey(): Boolean =
        AiSettings.provider(appContext) == AiProvider.Device || AiSettings.apiKey(appContext).isNotBlank()

    suspend fun askGemini(history: List<AskMessage>): Result<String> =
        withContext(Dispatchers.IO) { AiApi.generate(appContext, history).map { it.text } }

    fun searchApps(query: String, limit: Int = 7, includeWebFallback: Boolean = true): List<AppResult> {
        val needle = query.trim().lowercase()
        AppSearchShortcuts.fixedShortcut(needle)?.let { return listOf(it) }
        val destinations = AppSearchShortcuts.destinations(needle)
        val matches = (destinations + AppCatalog.search(appContext, query, limit).map {
            AppResult(label = it.label, packageName = it.packageName, icon = null)
        }).distinctBy { it.packageName }.take(limit)
        return if (matches.isEmpty() && needle.isNotBlank() && includeWebFallback && AppSearchSettings.webFallback.value) {
            listOf(AppResult("Search web for “${query.trim()}”", INTERNAL_WEB + Uri.encode(query.trim()), null))
        } else matches
    }

    fun allApps(query: String, limit: Int = Int.MAX_VALUE): List<AppResult> {
        val needle = query.trim().lowercase()
        return AppCatalog.all(appContext).asSequence()
            .filter { needle.isBlank() || needle in it.searchable }
            .take(limit)
            .map { AppResult(it.label, it.packageName, null) }
            .toList()
    }

    fun commanderRecents(query: String, limit: Int = 10): List<AppResult> {
        val needle = query.trim().lowercase()
        return recentApps(10).filter {
            needle.isBlank() || needle in it.label.lowercase() || needle in it.packageName.lowercase()
        }.take(limit)
    }

    private fun recentApps(limit: Int): List<AppResult> {
        val launchable = AppCatalog.all(appContext).associateBy { it.packageName }
        return AppLaunchHistory.packages(appContext)
            .mapNotNull(launchable::get)
            .take(limit.coerceAtMost(10))
            .map { AppResult(it.label, it.packageName, null) }
    }

    fun loadVisibleAppIcons(apps: List<AppResult>): List<AppResult> {
        return apps.map { app ->
            val icon = when (app.packageName) {
                INTERNAL_HOME -> ContextCompat.getDrawable(appContext, R.drawable.ic_commander_home)
                INTERNAL_SETTINGS -> ContextCompat.getDrawable(appContext, R.drawable.ic_commander_bar)
                INTERNAL_ALL_APPS, INTERNAL_RECENT_APPS -> ContextCompat.getDrawable(appContext, R.drawable.ic_commander_bar)
                INTERNAL_SWITCHER -> ContextCompat.getDrawable(appContext, R.drawable.ic_commander_bar)
                INTERNAL_KEY_CONTROL -> null
                INTERNAL_QUICK_CONTROLS -> ContextCompat.getDrawable(appContext, R.drawable.ic_quick_controls)
                INTERNAL_APP_SHORTCUTS -> ContextCompat.getDrawable(appContext, R.drawable.ic_app_shortcuts)
                else -> if (app.packageName.startsWith(INTERNAL_WEB)) null else AppIconOverrides.load(appContext, app.packageName)
                    ?: runCatching { appContext.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
            }
            app.copy(icon = icon, adaptiveColor = icon?.let(::extractIconColor))
        }
    }

    fun webFallbackResult(query: String): AppResult? {
        val value = query.trim()
        return if (value.isNotEmpty() && AppSearchSettings.webFallback.value) {
            AppResult("Search web for “$value”", INTERNAL_WEB + Uri.encode(value), null)
        } else null
    }

    fun loadPackageIcon(packageName: String) =
        runCatching { appContext.packageManager.getApplicationIcon(packageName) }.getOrNull()

    fun loadSystemSettingsIcon() = runCatching {
        val packageName = Intent(Settings.ACTION_SETTINGS).resolveActivity(appContext.packageManager)?.packageName
            ?: return@runCatching null
        appContext.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    private fun extractIconColor(drawable: android.graphics.drawable.Drawable): Int {
        val bitmap = drawable.toBitmap(32, 32)
        var bestScore = -1f
        var best = android.graphics.Color.rgb(255, 70, 35)
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val pixel = bitmap.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 150) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val score = hsv[1] * (.45f + hsv[2])
            if (score > bestScore && hsv[2] > .22f) {
                bestScore = score
                val tuned = floatArrayOf(hsv[0], hsv[1].coerceIn(.5f, .88f), hsv[2].coerceIn(.62f, .9f))
                best = android.graphics.Color.HSVToColor(tuned)
            }
        }
        return best
    }

    fun openAppInfo(activity: Activity, app: AppResult): Boolean {
        if (app.packageName.startsWith("internal:")) return false
        return runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                },
            )
            true
        }.getOrDefault(false)
    }

    fun matchAlias(input: String): AliasMatch? = AliasSettings.match(appContext, input)

    fun searchSystemSettings(query: String): List<SystemSettingResult> = SystemSettingsCatalog.search(query)

    fun openSystemSetting(activity: Activity, result: SystemSettingResult): String? = startOrFallback(
        activity,
        Intent(result.action),
        Intent(Settings.ACTION_SETTINGS),
        "Settings could not be opened",
    )

    fun openAlias(activity: Activity, match: AliasMatch): String? {
        if (match.query.isBlank()) return "Type a search after the alias"
        if (match.target.id == "gmail") {
            val launch = appContext.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                ?: return "Gmail is unavailable"
            if (HomeTypingAccessibilityService.isConnected()) {
                GmailSearchBridge.arm(appContext, match.query)
                return startOrFallback(activity, launch, null, "Gmail is unavailable")
            }
            val search = Intent(Intent.ACTION_SEARCH).apply {
                setClassName("com.google.android.gm", "com.google.android.gm.ui.MailActivityGmail")
                putExtra(SearchManager.QUERY, match.query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val encoded = Uri.encode(match.query)
            return startOrFallback(
                activity,
                search,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/mail/u/0/#search/$encoded")),
                "Gmail is unavailable",
            )
        }
        if (match.target.id == "onepassword") {
            val search = Intent("onepassword.action.SEARCH").apply {
                setClassName("com.onepassword.android", "com.onepassword.android.app.StartActivity")
            }
            val launch = appContext.packageManager.getLaunchIntentForPackage("com.onepassword.android")
                ?: appContext.packageManager.getLaunchIntentForPackage("com.agilebits.onepassword")
            OnePasswordSearchBridge.arm(appContext, match.query)
            return startOrFallback(activity, search, launch, "1Password is unavailable")
        }
        if (match.target.id == "waze" && match.isDirections) {
            val destination = match.routeDestination
                ?: return "Waze routes must have a destination"
            val uri = Uri.parse("https://www.waze.com/ul").buildUpon()
                .appendQueryParameter("q", destination)
                .appendQueryParameter("navigate", "yes")
                .appendQueryParameter("utm_source", "houston")
                .build()
            val targeted = Intent(Intent.ACTION_VIEW, uri).setPackage("com.waze")
            return startOrFallback(activity, targeted, Intent(Intent.ACTION_VIEW, uri), "Waze is unavailable")
        }
        if (match.target.id == "maps" && match.isDirections) {
            val builder = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                .appendQueryParameter("api", "1")
            match.routeOrigin?.let { builder.appendQueryParameter("origin", it) }
            match.routeDestination?.let { builder.appendQueryParameter("destination", it) }
            match.travelMode?.let { builder.appendQueryParameter("travelmode", it) }
            val uri = builder.build()
            val targeted = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
            return startOrFallback(activity, targeted, Intent(Intent.ACTION_VIEW, uri), "Google Maps is unavailable")
        }
        val encoded = URLEncoder.encode(match.query, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        val uri = Uri.parse(match.target.urlTemplate.replace("%s", encoded))
        val targeted = Intent(Intent.ACTION_VIEW, uri).apply { match.target.packageName?.let(::setPackage) }
        val fallback = Intent(Intent.ACTION_VIEW, uri)
        return startOrFallback(activity, targeted, fallback, "${match.target.label} is unavailable")
    }

    fun launchApp(activity: Activity, app: AppResult): String? {
        val intent = when (app.packageName) {
            INTERNAL_HOME -> internalDestination(activity, MainActivity::class.java)
            INTERNAL_SETTINGS -> internalDestination(activity, SettingsActivity::class.java)
            INTERNAL_SWITCHER -> return "Open App Switcher from Houston"
            INTERNAL_KEY_CONTROL -> internalDestination(activity, KeyControlActivity::class.java)
            INTERNAL_QUICK_CONTROLS, INTERNAL_APP_SHORTCUTS -> return "Open this category from Houston"
            else -> if (app.packageName.startsWith(INTERNAL_WEB)) {
                return executeWeb(activity, Uri.decode(app.packageName.removePrefix(INTERNAL_WEB)))
            } else appContext.packageManager.getLaunchIntentForPackage(app.packageName) ?: return "App cannot be opened"
        }
        startActivityAndFinish(activity, intent)
        if (!app.packageName.startsWith("internal:")) {
            AppLaunchHistory.record(appContext, app.packageName)
        }
        return null
    }

    suspend fun executeTodo(activity: Activity, text: String): String? {
        return if (TodoistSettings.directEnabled(appContext)) {
            if (text.isBlank()) "Type a task after -"
            else withContext(Dispatchers.IO) {
                TodoistApi.quickAdd(appContext, TodoistNaturalLanguage.expandCompactTimes(text))
            }
        } else {
            shareToPackage(activity, TodoistNaturalLanguage.expandCompactTimes(text), "com.todoist", "Todoist")
        }
    }

    fun executeEvent(activity: Activity, text: String): String? {
        if (text.isBlank()) return "Type an event after *"
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, text)
        }
        return startOrFallback(activity, intent, null, "No calendar app is installed")
    }

    fun executeWeb(activity: Activity, text: String): String? {
        if (text.isBlank()) return "Type a search after /"
        val target = if (Regex("(?i)^https?://").containsMatchIn(text)) text else {
            "https://www.google.com/search?q=" + URLEncoder.encode(text, StandardCharsets.UTF_8.toString())
        }
        return startOrFallback(activity, Intent(Intent.ACTION_VIEW, Uri.parse(target)), null, "No browser is installed")
    }

    fun executeNote(activity: Activity, text: String): String? =
        shareToPackage(activity, text, "com.google.android.keep", "Google Keep")

    fun executeAsk(activity: Activity, text: String): String? {
        if (text.isBlank()) return "Type a question after ?"
        val direct = text.startsWith('?')
        val prompt = if (direct) text.drop(1).trim() else text.trim()
        if (prompt.isBlank()) return "Type a question after ${if (direct) "??" else "?"}"
        if (!direct) ChatGptNotificationBridge.arm(appContext, prompt)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
            setPackage("com.openai.chatgpt")
        }
        if (direct) return startOrFallback(activity, intent, null, "ChatGPT is not installed")
        if (intent.resolveActivity(appContext.packageManager) == null) return "ChatGPT is not installed"
        return runCatching {
            startActivityAndFinish(activity, intent)
            null
        }.getOrElse { "Could not open ChatGPT" }
    }

    fun openChatGpt(activity: Activity): String? {
        val intent = appContext.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
            ?: return "ChatGPT is not installed"
        return startOrFallback(activity, intent, null, "ChatGPT is not installed")
    }

    fun openCamera(activity: Activity): String? = startOrFallback(
        activity,
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        null,
        "No camera app is installed",
    )

    fun openMemo(activity: Activity): String? = startOrFallback(
        activity,
        Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION),
        null,
        "No recorder app is installed",
    )

    private fun internalDestination(activity: Activity, destination: Class<out Activity>) =
        Intent(activity, destination).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )

    internal companion object {
        const val INTERNAL_HOME = "internal:command-home"
        const val INTERNAL_SETTINGS = "internal:command-settings"
        const val INTERNAL_WEB = "internal:web-search:"
        const val INTERNAL_ALL_APPS = "internal:all-apps"
        const val INTERNAL_RECENT_APPS = "internal:recent-apps"
        const val INTERNAL_SWITCHER = "internal:switcher"
        const val INTERNAL_KEY_CONTROL = "internal:key-control"
        const val INTERNAL_TASKS = "internal:todoist-tasks"
        const val INTERNAL_QUICK_CONTROLS = "internal:quick-controls"
        const val INTERNAL_APP_SHORTCUTS = "internal:app-shortcuts"
    }

    private fun shareToPackage(activity: Activity, text: String, packageName: String, label: String): String? {
        if (text.isBlank()) return "Type something after the command"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage(packageName)
        }
        return startOrFallback(activity, intent, Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Send to…"), "$label is not installed")
    }

    private fun startOrFallback(activity: Activity, primary: Intent, fallback: Intent?, unavailable: String): String? {
        val pm = appContext.packageManager
        val chosen = if (primary.resolveActivity(pm) != null) primary else fallback
        if (chosen == null || chosen.resolveActivity(pm) == null) return unavailable
        return runCatching {
            startActivityAndFinish(activity, chosen)
            null
        }.getOrElse { error ->
            if (error is SecurityException) "Permission denied by the target app" else unavailable
        }
    }

    private fun startActivityAndFinish(activity: Activity, intent: Intent) {
        val launchOptions = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        activity.startActivity(intent, launchOptions)
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}

/** Pure built-in shortcut helpers (no Android deps) for unit testing. */
internal object AppSearchShortcuts {
    fun fixedShortcut(needle: String): AppResult? = when (needle) {
        "all" -> AppResult("All apps", "internal:all-apps", null)
        "rec", "recents" -> AppResult("Recent apps", "internal:recent-apps", null)
        else -> null
    }

    fun destinations(needle: String): List<AppResult> = buildList {
        if ("houston settings".contains(needle) || "settings".startsWith(needle)) {
            add(AppResult("Houston Settings", "internal:command-settings", null))
        }
        if ("switcher".startsWith(needle) || "app switcher".contains(needle)) {
            add(AppResult("App Switcher", "internal:switcher", null))
        }
        if ("key control".contains(needle) || "control screen".contains(needle)) {
            add(AppResult("Key Control", "internal:key-control", null))
        }
        if ("tasks".startsWith(needle) || "todoist tasks".contains(needle)) {
            add(AppResult("Tasks", "internal:todoist-tasks", null))
        }
        if ("quick controls".contains(needle) || "device controls".contains(needle)) {
            add(AppResult("Quick Controls", "internal:quick-controls", null))
        }
        if ("app shortcuts".contains(needle) || "shortcuts".startsWith(needle)) {
            add(AppResult("App Shortcuts", "internal:app-shortcuts", null))
        }
    }
}
