package com.astroboii47.commander

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result dispatch moved verbatim out of [NativeSearchActivity].
 * Uses startActivity only; no PendingIntent/Beeper/Hub paths.
 * The Activity keeps a thin [NativeSearchActivity.openResult] delegate so
 * call sites and signatures stay stable.
 */
internal fun NativeSearchActivity.performOpenResult(result: NativeResult, query: String) {
    if (query.isNotBlank()) CommandBarHistory.recordSearch(this, query)
    if (result.kind == NativeKind.ScreenshotCapture) {
        screenshotPending = true
        if (EdgeBarSettings.enabled(this)) hideEdgeHandle()
        finish()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(650)
            val error = DeviceControls.captureScreenshot(applicationContext)
            kotlinx.coroutines.delay(1200)
            withContext(Dispatchers.Main) {
                if (error != null) Toast.makeText(applicationContext, error, Toast.LENGTH_LONG).show()
                screenshotPending = false
                edgeRestoreSent = false
                if (EdgeBarSettings.enabled(applicationContext)) runCatching {
                    androidx.core.content.ContextCompat.startForegroundService(applicationContext,
                        Intent(applicationContext, EdgeBarService::class.java).setAction(EdgeBarService.ACTION_SHOW))
                }
            }
        }
        return
    }
    if (result.kind == NativeKind.BluetoothToggle) {
        if (!DeviceControls.bluetoothPermission(this)) {
            if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val error = DeviceControls.setBluetooth(!DeviceControls.bluetoothEnabled(this@performOpenResult))
            withContext(Dispatchers.Main) { Toast.makeText(this@performOpenResult, error ?: "Bluetooth updated", Toast.LENGTH_SHORT).show() }
        }
        return
    }
    if (result.kind == NativeKind.BluetoothAccess) {
        if (Build.VERSION.SDK_INT >= 31) bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        return
    }
    if (result.kind == NativeKind.BluetoothSettings || result.kind == NativeKind.BluetoothDevice) {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        finish()
        return
    }
    if (result.kind == NativeKind.Clipboard) {
        CommandBarHistory.copy(this, result.clipboard.orEmpty())
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        finish()
        return
    }
    if (result.kind == NativeKind.MusicOpen) {
        if (CommandBarDeviceActions.openPlayer(this)) finish()
        else Toast.makeText(this, "No active player", Toast.LENGTH_SHORT).show()
        return
    }
    if (result.kind == NativeKind.ScreenshotAccess) {
        imagePermission.launch(CommandBarDeviceActions.imagePermissions())
        return
    }
    if (result.kind in setOf(NativeKind.MusicPrevious, NativeKind.MusicToggle, NativeKind.MusicNext)) {
        val action = when (result.kind) {
            NativeKind.MusicPrevious -> "previous"
            NativeKind.MusicNext -> "next"
            else -> "toggle"
        }
        runCatching { CommandBarDeviceActions.mediaAction(this, action) }
            .onFailure { Toast.makeText(this, "Music control unavailable", Toast.LENGTH_SHORT).show() }
        mediaRevision++
        return
    }
    if (result.kind == NativeKind.Screenshot) {
        val uri = result.screenshot?.uri ?: return
        runCatching {
            (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newUri(contentResolver, "Screenshot", uri))
        }.onSuccess {
            Toast.makeText(this, "Screenshot copied", Toast.LENGTH_SHORT).show()
            finish()
        }.onFailure { Toast.makeText(this, "Could not copy screenshot", Toast.LENGTH_SHORT).show() }
        return
    }
    lifecycleScope.launch {
        val error = when (result.kind) {
            NativeKind.App -> engine.execute(ParsedCommand(CommandKind.Apps, result.title), result.app)
            NativeKind.File -> result.file?.let(engine::openFile)
            NativeKind.Setting -> result.setting?.let(engine::openSystemSetting)
            NativeKind.Calculator -> engine.execute(ParsedCommand(CommandKind.Calculator, query), null)
            NativeKind.Web -> engine.execute(ParsedCommand(CommandKind.Web, query), null)
            NativeKind.Command -> engine.execute(parseCommand(query), null)
            NativeKind.FileAccess -> engine.openFileSearchAccess()
            NativeKind.BrowseFiles -> {
                startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE))
                null
            }
            NativeKind.OpenSettings, NativeKind.CommanderSetting -> {
                startActivity(Intent(this@performOpenResult, SettingsActivity::class.java).putExtra("focus", result.title)); null
            }
            NativeKind.ScreenshotMenu, NativeKind.MusicMenu,
            NativeKind.ScreenshotAccess, NativeKind.Screenshot,
            NativeKind.MusicPrevious, NativeKind.MusicToggle, NativeKind.MusicNext,
            NativeKind.MusicOpen, NativeKind.Clipboard, NativeKind.SearchHistory, NativeKind.ClipboardMenu, NativeKind.DownloadsMenu, NativeKind.AiMenu, NativeKind.AiPrompt, NativeKind.Weather, NativeKind.Brightness, NativeKind.Volume, NativeKind.ScreenshotCapture, NativeKind.BluetoothToggle, NativeKind.BluetoothSettings, NativeKind.BluetoothDevice, NativeKind.BluetoothAccess -> null
        }
        if (error == null) finish() else Toast.makeText(this@performOpenResult, error, Toast.LENGTH_SHORT).show()
    }
}
