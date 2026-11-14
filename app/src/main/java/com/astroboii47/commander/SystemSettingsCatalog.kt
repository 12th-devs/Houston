package com.astroboii47.commander

data class SystemSettingResult(
    val label: String,
    val subtitle: String,
    val action: String,
    val keywords: List<String> = emptyList(),
)

object SystemSettingsCatalog {
    private fun e(label: String, subtitle: String, action: String, keywords: List<String> = emptyList()) =
        SystemSettingResult(label, subtitle, action, keywords)
    private val destinations = listOf(
        e("Wi-Fi", "Networks and internet", "android.settings.WIFI_SETTINGS", listOf("wifi", "internet", "network")),
        e("Bluetooth", "Connected devices", "android.settings.BLUETOOTH_SETTINGS", listOf("bluetooth", "pair", "device")),
        e("Battery", "Battery usage and power", "android.settings.BATTERY_SETTINGS", listOf("battery", "power", "saver")),
        e("Display", "Brightness, size and screen", "android.settings.DISPLAY_SETTINGS", listOf("display", "brightness", "screen", "dpi")),
        e("Sound", "Volume, vibration and audio", "android.settings.SOUND_SETTINGS", listOf("sound", "volume", "audio", "vibration")),
        e("Notifications", "App notification controls", "android.settings.NOTIFICATION_SETTINGS", listOf("notifications", "alerts")),
        e("Notification access", "Apps that can read notifications", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", listOf("notification access", "listener")),
        e("Accessibility", "Accessibility services", "android.settings.ACCESSIBILITY_SETTINGS", listOf("accessibility", "service")),
        e("Default apps", "Browser, phone, launcher and more", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("default apps", "browser", "launcher", "home")),
        e("Apps", "Installed apps and permissions", "android.settings.APPLICATION_SETTINGS", listOf("apps", "applications", "installed", "permissions")),
        e("Keyboard", "Keyboards and input methods", "android.settings.INPUT_METHOD_SETTINGS", listOf("keyboard", "ime", "input")),
        e("Language", "Languages and regional options", "android.settings.LOCALE_SETTINGS", listOf("language", "locale", "region")),
        e("Location", "Location access and services", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("location", "gps")),
        e("Privacy", "Privacy controls", "android.settings.PRIVACY_SETTINGS", listOf("privacy", "permissions")),
        e("Security", "Device security", "android.settings.SECURITY_SETTINGS", listOf("security", "lock", "password")),
        e("Storage", "Device storage usage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("storage", "space", "files")),
        e("Date and time", "Clock and time zone", "android.settings.DATE_SETTINGS", listOf("date", "time", "timezone")),
        e("Developer options", "Android developer settings", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("developer", "adb", "debug", "animation")),
        e("All files access", "Special file access", "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION", listOf("all files", "storage permission")),
    )

    fun search(query: String): List<SystemSettingResult> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return destinations
        return destinations.filter { result ->
            result.label.contains(needle, true) || result.subtitle.contains(needle, true) ||
                result.keywords.any { it.contains(needle, true) || needle.contains(it, true) }
        }.sortedWith(compareBy<SystemSettingResult> { !it.label.startsWith(needle, true) }.thenBy { it.label })
    }
}
