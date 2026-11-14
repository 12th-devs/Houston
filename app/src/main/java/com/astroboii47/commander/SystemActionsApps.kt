package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SystemActionsApps {
    fun apps(): List<SystemAction> = listOf(
        SystemAction(
            id = "app.launch", name = "Launch app",
            description = "Launch an installed app by name or package.",
            category = ActionCategory.READ_ONLY,
            params = listOf(
                ParamSpec("query", ParamType.TEXT, required = true, description = "App name"),
                ParamSpec("package", ParamType.TEXT, description = "Exact package name"),
            ),
            execute = { ctx, args ->
                val pkg = args["package"]?.takeIf(String::isNotBlank)
                    ?: findAppPackage(ctx, args["query"].orEmpty())
                    ?: return@SystemAction ActionResult.fail("No matching app found")
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    ?: return@SystemAction ActionResult.fail("App cannot be opened")
                if (launchIntent(ctx, intent)) {
                    AppLaunchHistory.record(ctx, pkg)
                    ActionResult.ok("Opening ${args["query"] ?: pkg}", mapOf("package" to pkg))
                } else ActionResult.fail("App cannot be opened")
            },
        ),
        SystemAction(
            id = "app.shortcut.launch", name = "Launch app shortcut",
            description = "Launch an Android app shortcut by label.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                val query = args["query"].orEmpty()
                val shortcut = AppShortcutCatalog.search(ctx, query).firstOrNull()
                    ?: return@SystemAction ActionResult.fail("No matching app shortcut")
                val error = AppShortcutCatalog.launch(ctx, shortcut)
                if (error == null) ActionResult.ok("Opening ${shortcut.label}")
                else ActionResult.fail(error)
            },
        ),
        SystemAction(
            id = "app.info", name = "App info",
            description = "Open Android app info for an installed app.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                val pkg = findAppPackage(ctx, args["query"].orEmpty())
                    ?: return@SystemAction ActionResult.fail("No matching app found")
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                if (launchIntent(ctx, intent)) ActionResult.ok("App info opened")
                else ActionResult.fail("App info is unavailable")
            },
        ),
        SystemAction(
            id = "settings.open", name = "Open settings",
            description = "Open an Android system setting by keyword.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            execute = { ctx, args ->
                val result = SystemSettingsCatalog.search(args["query"].orEmpty()).firstOrNull()
                    ?: return@SystemAction ActionResult.fail("No matching setting")
                if (launchIntent(ctx, Intent(result.action))) ActionResult.ok("Opening ${result.label}")
                else ActionResult.fail("Settings could not be opened")
            },
        ),
        SystemAction(
            id = "share.text", name = "Share text",
            description = "Share text through Android's share sheet.",
            category = ActionCategory.CONSEQUENTIAL,
            params = listOf(ParamSpec("text", ParamType.TEXT, required = true, maxLength = 5000)),
            execute = { ctx, args ->
                val text = args["text"].orEmpty()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                if (launchIntent(ctx, Intent.createChooser(intent, "Share"))) ActionResult.ok("Share sheet opened")
                else ActionResult.fail("Sharing is unavailable")
            },
        ),
        SystemAction(
            id = "web.search", name = "Web search",
            description = "Open a web search in the browser.",
            category = ActionCategory.READ_ONLY,
            params = listOf(ParamSpec("query", ParamType.TEXT, required = true)),
            aliases = setOf("web_search"),
            execute = { ctx, args ->
                val query = args["query"].orEmpty()
                val target = if (Regex("(?i)^https?://").containsMatchIn(query)) query
                else "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                if (launchIntent(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(target)))) ActionResult.ok("Web search opened")
                else ActionResult.fail("No browser is installed")
            },
        ),
    )

    private fun findAppPackage(ctx: Context, query: String): String? {
        val needle = query.trim()
        if (needle.isEmpty()) return null
        if (needle.startsWith("package:")) return needle.removePrefix("package:").trim().takeIf(String::isNotBlank)
        return AppCatalog.search(ctx, needle, 1).firstOrNull()?.packageName
    }
}
