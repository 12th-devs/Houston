package com.astroboii47.commander

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceControls {
    enum class Level { Brightness, Volume }
    fun level(context: Context, kind: Level): Int = when (kind) {
        Level.Brightness -> runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255 }.getOrDefault(50)
        Level.Volume -> (context.getSystemService(AudioManager::class.java)).let { it.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    }
    fun setLevel(context: Context, kind: Level, percent: Int): String? {
        val value = percent.coerceIn(0, 100)
        return when (kind) {
            Level.Volume -> { val audio = context.getSystemService(AudioManager::class.java); audio.setStreamVolume(AudioManager.STREAM_MUSIC, (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * value / 100f).toInt(), 0); null }
            Level.Brightness -> if (!Settings.System.canWrite(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(android.net.Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Allow Modify system settings, then try again"
            } else {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (value * 255 / 100).coerceAtLeast(1))
                null
            }
        }
    }
    suspend fun captureScreenshot(context: Context): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        if (HomeTypingAccessibilityService.takeSystemScreenshot()) null
        else "Enable Houston accessibility service in Android settings to take screenshots"
    }
    fun bluetoothAdapter(context: Context): BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    fun bluetoothPermission(context: Context) = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    data class BluetoothEntry(val name: String, val connected: Boolean)
    fun bluetoothDevices(context: Context): List<BluetoothEntry> {
        val connected = runCatching {
            context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST) }
                .map { it.productName.toString() }.filter(String::isNotBlank)
        }.getOrDefault(emptyList())
        val paired = if (!bluetoothPermission(context)) emptyList() else runCatching {
            bluetoothAdapter(context)?.bondedDevices?.map { it.name ?: it.address }.orEmpty()
        }.getOrDefault(emptyList())
        return (connected + paired).distinct().sorted().map { BluetoothEntry(it, it in connected) }
    }
    fun pairedDevices(context: Context): List<String> = bluetoothDevices(context).map { it.name }
    fun bluetoothEnabled(context: Context): Boolean = if (!bluetoothPermission(context)) false else runCatching { bluetoothAdapter(context)?.isEnabled == true }.getOrDefault(false)
    fun setBluetooth(enabled: Boolean): String? = ShizukuBridge.run(arrayOf("cmd", "bluetooth_manager", if (enabled) "enable" else "disable"))
}
