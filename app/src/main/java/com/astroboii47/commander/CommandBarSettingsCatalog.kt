package com.astroboii47.commander

/** Searchable actions and settings exposed by the current command bar UI. */
object CommandBarSettingsCatalog {
    data class Entry(val title: String, val section: String, val terms: String = "")
    private fun e(title: String, section: String, terms: String = "") = Entry(title, section, terms)
    val entries = listOf(
        e("Edge handle", "Access", "swipe gesture overlay"),
        e("Handle side", "Access", "left right"),
        e("Handle size", "Access", "small medium large"),
        e("Swipe distance", "Access", "gesture sensitivity"),
        e("App usage access", "Access", "ranking screen time"),
        e("Screenshot access", "Access", "photos permission"),
        e("Notification access", "Access", "music media session"),
        e("Bluetooth device access", "Access", "paired devices permission"),
        e("File access", "Access", "downloads files storage"),
        e("AI settings", "AI", "provider model key"),
        e("Screenshot capture", "Access", "accessibility screenshot"),
        e("Theme", "Appearance", "dark light system material you dynamic color"),
        e("Material You command bar", "Appearance", "dynamic color wallpaper icons"),
        e("Command bar height", "Appearance", "panel size percent"),
        e("Surface style", "Appearance", "solid frosted translucent blur"),
        e("Music mini player", "Appearance", "song controls"),
        e("Compact rows", "Appearance", "density"),
        e("Accent color", "Appearance", "violet cyan amber mint"),
        e("Visible results", "Appearance", "count"),
        e("Show keyboard", "Appearance", "auto focus"),
        e("Search screenshots", "Search", "images"),
        e("Search files", "Search", "downloads documents"),
        e("History results", "Search", "clipboard past searches"),
        e("Default assistant", "Assistant", "role gesture voice assist"),
        e("Share screen context", "Assistant", "screenshot assist context"),
        e("Voice input", "Assistant", "microphone speech"),
        e("Assistant logs", "Assistant", "diagnostics logcat copy debug"),
        e("Assistant diagnostics", "Assistant", "diagnostics default active service"),
        e("Clear clipboard history", "Privacy", "delete clips"),
        e("Clear search history and ranking", "Privacy", "reset learning"),
        e("Command symbols", "Help", "! # @ / ? - + * comma"),
        e("AI provider", "AI", "Gemini Nano on-device AICore OpenRouter OpenAI Anthropic"),
        e("AI model", "AI", "model name id"),
        e("AI API key", "AI", "encrypted provider credential"),
    )
    fun search(query: String) = entries.filter { entry ->
        val needle = query.trim()
        needle.isNotEmpty() && (entry.title.contains(needle, true) || entry.terms.split(' ').any { it.startsWith(needle, true) })
    }
}
