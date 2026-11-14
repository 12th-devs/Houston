package com.astroboii47.commander

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Restarts the edge handle after reboot / app update when the user left it
 * enabled. Without this the sidebar silently disappears on every restart.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!relevant) return
        // Best-effort: re-queue the morning briefing alarm on every boot/update.
        runCatching { HoustonScheduler.reschedule(context) }
        if (!EdgeBarSettings.enabled(context)) return
        // Overlay windows are unavailable until the user unlocks, so a direct-boot
        // delivery only records intent; USER_UNLOCKED / BOOT_COMPLETED performs it.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, EdgeBarService::class.java))
        }
    }
}
