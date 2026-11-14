package com.astroboii47.commander

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build

class MinimalCommandApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        AccentSettings.initialize(this)
        TriggerSettings.initialize(this)
        FileSearchSettings.initialize(this)
        AppearanceSettings.initialize(this)
        AppSearchSettings.initialize(this)
        SoundSettings.initialize(this)
        SoundFeedback.initialize(this)
        HomeTypingSettings.initialize(this)
        HardwareShortcutSettings.initialize(this)
        KeyControlSettings.initialize(this)
        AppCatalog.initialize(this)
        // The edge handle must survive kills, updates and force-stops: boot only
        // covers restarts, so re-attach on every process launch when enabled.
        EdgeBarSettings.ensureStarted(this)
        publishDynamicShortcuts()
    }

    private fun publishDynamicShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val commandBar = ShortcutInfo.Builder(this, COMMAND_BAR_SHORTCUT_ID)
            .setShortLabel(getString(R.string.shortcut_command_bar_short))
            .setLongLabel(getString(R.string.shortcut_command_bar_long))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_commander_bar))
            .setIntent(
                Intent("com.astroboii47.commander.OPEN_COMMAND_BAR")
                    .setClass(this, NativeSearchActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            .build()
        val settings = ShortcutInfo.Builder(this, SETTINGS_SHORTCUT_ID)
            .setShortLabel(getString(R.string.shortcut_settings_short))
            .setLongLabel(getString(R.string.shortcut_settings_long))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_commander_bar))
            .setIntent(Intent(Intent.ACTION_VIEW).setClass(this, SettingsActivity::class.java))
            .build()
        val switcher = ShortcutInfo.Builder(this, SWITCHER_SHORTCUT_ID)
            .setShortLabel("App Switcher")
            .setLongLabel("Open Houston App Switcher")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_commander_bar))
            .setIntent(
                Intent("com.astroboii47.commander.OPEN_SWITCHER")
                    .setClass(this, SwitcherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            .build()
        val keyControl = ShortcutInfo.Builder(this, KEY_CONTROL_SHORTCUT_ID)
            .setShortLabel("Key Control")
            .setLongLabel("Control the screen with keys")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_commander_bar))
            .setIntent(
                Intent("com.astroboii47.commander.OPEN_KEY_CONTROL")
                    .setClass(this, KeyControlActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            .build()
        runCatching {
            manager.removeAllDynamicShortcuts()
            manager.addDynamicShortcuts(listOf(commandBar, switcher, keyControl, settings))
        }
    }

    private companion object {
        const val COMMAND_BAR_SHORTCUT_ID = "command_bar_dynamic"
        const val SETTINGS_SHORTCUT_ID = "command_settings_dynamic"
        const val SWITCHER_SHORTCUT_ID = "app_switcher_dynamic"
        const val KEY_CONTROL_SHORTCUT_ID = "key_control_dynamic"
    }
}
