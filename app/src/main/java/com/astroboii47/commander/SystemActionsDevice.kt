package com.astroboii47.commander

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

object SystemActionsDevice {
    fun device(): List<SystemAction> = listOf(
        SystemAction(
            id = "device.flashlight.toggle", name = "Toggle flashlight",
            description = "Toggle the camera flashlight on or off.",
            category = ActionCategory.REVERSIBLE,
            permissions = listOf(Manifest.permission.CAMERA),
            available = { ctx -> ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) },
            execute = { ctx, _ ->
                if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                    ActionResult.fail("This device has no flashlight")
                } else if (missingPermissions(ctx, find("device.flashlight.toggle")).isNotEmpty()) {
                    ActionResult.fail(
                        "Camera permission is needed for the flashlight",
                        mapOf("permission" to Manifest.permission.CAMERA),
                    )
                } else {
                    val error = toggleFlashlight(ctx)
                    if (error == null) ActionResult.ok("Flashlight ${if (FlashlightState.enabled) "on" else "off"}")
                    else ActionResult.fail(error)
                }
            },
        ),
        SystemAction(
            id = "device.brightness.get", name = "Get brightness",
            description = "Get the current screen brightness percentage.",
            category = ActionCategory.READ_ONLY,
            execute = { ctx, _ -> ActionResult.ok("Brightness ${DeviceControls.level(ctx, DeviceControls.Level.Brightness)}%") },
        ),
        SystemAction(
            id = "device.brightness.set", name = "Set brightness",
            description = "Set screen brightness (0–100) or automatic mode.",
            category = ActionCategory.REVERSIBLE,
            params = listOf(
                ParamSpec("percent", ParamType.INT, min = 0.0, max = 100.0, description = "0 to 100"),
                ParamSpec("automatic", ParamType.BOOLEAN, description = "Use automatic brightness"),
            ),
            aliases = setOf("brightness_set"),
            execute = { ctx, args ->
                val automatic = args["automatic"]?.toBooleanStrictOrNull() == true
                val percent = args["percent"]?.toIntOrNull()
                if (!automatic && percent == null) {
                    ActionResult.fail("Provide percent (0–100) or automatic true")
                } else if (automatic) {
                    val ok = runCatching {
                        Settings.System.putInt(
                            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                        )
                        true
                    }.getOrDefault(false)
                    if (ok) ActionResult.ok("Automatic brightness on")
                    else ActionResult.fail(
                        "Allow Modify system settings, then try again",
                        mapOf("settings" to Settings.ACTION_MANAGE_WRITE_SETTINGS),
                    )
                } else {
                    val level = percent ?: return@SystemAction ActionResult.fail("Provide percent (0–100) or automatic true")
                    val error = DeviceControls.setLevel(ctx, DeviceControls.Level.Brightness, level)
                    if (error == null) ActionResult.ok("Brightness set to $level%")
                    else ActionResult.fail(error, mapOf("settings" to Settings.ACTION_MANAGE_WRITE_SETTINGS))
                }
            },
        ),
        SystemAction(
            id = "device.volume.get", name = "Get volume",
            description = "Get the current media volume percentage.",
            category = ActionCategory.READ_ONLY,
            aliases = setOf("volume_get"),
            execute = { ctx, _ -> ActionResult.ok("Media volume ${DeviceControls.level(ctx, DeviceControls.Level.Volume)}%") },
        ),
        SystemAction(
            id = "device.volume.set", name = "Set volume",
            description = "Set media volume (0–100).",
            category = ActionCategory.REVERSIBLE,
            params = listOf(ParamSpec("percent", ParamType.INT, required = true, min = 0.0, max = 100.0)),
            aliases = setOf("volume_set"),
            execute = { ctx, args ->
                val percent = args["percent"]?.toIntOrNull() ?: return@SystemAction ActionResult.fail("Provide percent (0–100)")
                val error = DeviceControls.setLevel(ctx, DeviceControls.Level.Volume, percent)
                if (error == null) ActionResult.ok("Media volume set to $percent%")
                else ActionResult.fail(error)
            },
        ),
        SystemAction(
            id = "device.ringer.set", name = "Set ringer mode",
            description = "Set sound, vibrate, silent or Do Not Disturb.",
            category = ActionCategory.REVERSIBLE,
            params = listOf(ParamSpec("mode", ParamType.TEXT, required = true, description = "sound, vibrate, silent or dnd")),
            execute = { ctx, args ->
                when (val mode = args["mode"]?.lowercase()) {
                    "sound", "normal", "on" -> setRinger(ctx, AudioManager.RINGER_MODE_NORMAL, "Sound on")
                    "vibrate" -> setRinger(ctx, AudioManager.RINGER_MODE_VIBRATE, "Vibrate on")
                    "silent", "off" -> setRinger(ctx, AudioManager.RINGER_MODE_SILENT, "Silent on")
                    "dnd" -> {
                        val notifications = ctx.getSystemService(NotificationManager::class.java)
                        if (notifications?.isNotificationPolicyAccessGranted != true) {
                            launchIntent(ctx, settingIntent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            ActionResult.fail("Allow Do Not Disturb access, then try again")
                        } else {
                            notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                            ActionResult.ok("Do Not Disturb on")
                        }
                    }
                    else -> ActionResult.fail("Use sound, vibrate, silent or dnd")
                }
            },
        ),
        SystemAction(
            id = "device.rotation.toggle", name = "Toggle auto-rotation",
            description = "Toggle automatic screen rotation.",
            category = ActionCategory.REVERSIBLE,
            execute = { ctx, _ ->
                if (!Settings.System.canWrite(ctx)) {
                    launchIntent(ctx, settingIntent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
                    ActionResult.fail("Allow Modify system settings, then try again")
                } else {
                    val enabled = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                    Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (enabled) 0 else 1)
                    ActionResult.ok("Auto-rotation ${if (enabled) "off" else "on"}")
                }
            },
        ),
        SystemAction(
            id = "device.dnd.status", name = "Do Not Disturb status",
            description = "Check whether Do Not Disturb is on.",
            category = ActionCategory.READ_ONLY,
            execute = { ctx, _ ->
                val notifications = ctx.getSystemService(NotificationManager::class.java)
                val filter = runCatching { notifications?.currentInterruptionFilter }.getOrNull()
                ActionResult.ok(if (filter == NotificationManager.INTERRUPTION_FILTER_NONE) "Do Not Disturb is on" else "Do Not Disturb is off")
            },
        ),
        SystemAction(
            id = "device.battery.status", name = "Battery status",
            description = "Get battery level and charging state.",
            category = ActionCategory.READ_ONLY,
            execute = { ctx, _ ->
                val manager = ctx.getSystemService(BatteryManager::class.java)
                val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val charging = runCatching {
                    val filter = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    filter?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                        filter?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                }.getOrDefault(false)
                if (level < 0) ActionResult.fail("Battery status is unavailable")
                else ActionResult.ok("Battery $level%${if (charging) " · charging" else ""}")
            },
        ),
        SystemAction(
            id = "device.connectivity.panel", name = "Connectivity panel",
            description = "Open the system internet connectivity panel.",
            category = ActionCategory.READ_ONLY,
            available = { Build.VERSION.SDK_INT >= 29 },
            execute = { ctx, _ ->
                val intent = if (Build.VERSION.SDK_INT >= 29) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                } else settingIntent(Settings.ACTION_WIFI_SETTINGS)
                if (launchIntent(ctx, intent)) ActionResult.ok("Connectivity panel opened")
                else ActionResult.fail("Connectivity settings are unavailable")
            },
        ),
        SystemAction(
            id = "device.bluetooth.status", name = "Bluetooth status",
            description = "Check whether Bluetooth is enabled.",
            category = ActionCategory.READ_ONLY,
            aliases = setOf("bluetooth_status"),
            execute = { ctx, _ -> ActionResult.ok("Bluetooth ${if (DeviceControls.bluetoothEnabled(ctx)) "on" else "off"}") },
        ),
        SystemAction(
            id = "device.bluetooth.devices", name = "Bluetooth devices",
            description = "List paired and connected Bluetooth devices.",
            category = ActionCategory.READ_ONLY,
            aliases = setOf("bluetooth_devices"),
            execute = { ctx, _ ->
                if (!DeviceControls.bluetoothPermission(ctx)) {
                    ActionResult.fail(
                        "Bluetooth permission is needed in Houston settings",
                        mapOf("permission" to "android.permission.BLUETOOTH_CONNECT"),
                    )
                } else {
                    val devices = DeviceControls.bluetoothDevices(ctx)
                    if (devices.isEmpty()) ActionResult.ok("No paired devices")
                    else ActionResult.ok(devices.joinToString("\n") { "${it.name} · ${if (it.connected) "connected" else "paired"}" })
                }
            },
        ),
        SystemAction(
            id = "device.bluetooth.set", name = "Set Bluetooth",
            description = "Turn Bluetooth on or off (uses Shizuku shell where required).",
            category = ActionCategory.REVERSIBLE,
            params = listOf(ParamSpec("enabled", ParamType.BOOLEAN, required = true)),
            aliases = setOf("bluetooth_set"),
            execute = { ctx, args ->
                val enabled = args["enabled"]?.toBooleanStrictOrNull() ?: return@SystemAction ActionResult.fail("Provide enabled true or false")
                val error = DeviceControls.setBluetooth(enabled)
                if (error == null) ActionResult.ok("Bluetooth change requested")
                else ActionResult.fail(error)
            },
        ),
        SystemAction(
            id = "media.status", name = "Music status",
            description = "Get the current song and playback state.",
            category = ActionCategory.READ_ONLY,
            aliases = setOf("music_status"),
            execute = { ctx, _ ->
                val player = CommandBarDeviceActions.player(ctx)
                if (player == null) ActionResult.ok("No active player")
                else ActionResult.ok("${player.title} · ${player.artist} · ${if (player.playing) "playing" else "paused"}")
            },
        ),
        SystemAction(
            id = "media.control", name = "Control music",
            description = "Play, pause, or skip the active media session.",
            category = ActionCategory.REVERSIBLE,
            params = listOf(ParamSpec("action", ParamType.TEXT, required = true, description = "play, pause, next, previous or toggle")),
            aliases = setOf("music_control"),
            execute = { ctx, args ->
                when (val action = args["action"]?.lowercase()) {
                    "toggle", "play", "pause" -> {
                        CommandBarDeviceActions.mediaAction(ctx, "toggle")
                        ActionResult.ok("Music action: $action")
                    }
                    "next" -> {
                        CommandBarDeviceActions.mediaAction(ctx, "next")
                        ActionResult.ok("Skipped to next track")
                    }
                    "previous" -> {
                        CommandBarDeviceActions.mediaAction(ctx, "previous")
                        ActionResult.ok("Back to previous track")
                    }
                    else -> ActionResult.fail("Use play, pause, next or previous")
                }
            },
        ),
        SystemAction(
            id = "media.open", name = "Open music player",
            description = "Open the currently active music app.",
            category = ActionCategory.READ_ONLY,
            execute = { ctx, _ ->
                if (CommandBarDeviceActions.openPlayer(ctx)) ActionResult.ok("Music player opened")
                else ActionResult.fail("No active player")
            },
        ),
    )

    private fun find(id: String): SystemAction = device().first { it.id == id }

    private fun setRinger(ctx: Context, mode: Int, message: String): ActionResult {
        return runCatching {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = mode
            ActionResult.ok(message)
        }.getOrElse { ActionResult.fail("Ringer mode is unavailable") }
    }

    private object FlashlightState { var enabled = false }

    private fun toggleFlashlight(ctx: Context): String? = runCatching {
        val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val camera = manager.cameraIdList.first { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        FlashlightState.enabled = !FlashlightState.enabled
        manager.setTorchMode(camera, FlashlightState.enabled)
        null
    }.getOrElse { "Flashlight is unavailable" }
}
