package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

object EdgeBarSettings {
    private const val PREFS = "edge_bar"
    fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    /** Re-attaches the pill without tearing it down; safe to call on every launch/resume. */
    fun ensureStarted(context: Context) {
        if (!enabled(context)) return
        if (!Settings.canDrawOverlays(context)) return
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, EdgeBarService::class.java))
        }
    }

    /** Full restart for an explicitly requested recovery (lost pill, side stuck, etc). */
    fun refresh(context: Context) {
        if (!enabled(context)) return
        runCatching {
            context.stopService(Intent(context, EdgeBarService::class.java))
            ContextCompat.startForegroundService(context, Intent(context, EdgeBarService::class.java))
        }
    }
    fun rightSide(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("right_side", true)
    fun handleSize(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("handle_size", 1).coerceIn(0, 2)
    fun setHandleSize(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("handle_size", value.coerceIn(0, 2)).apply()
        restart(context)
    }
    fun swipeDistance(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("swipe_distance", 24).coerceIn(12, 60)
    fun setSwipeDistance(context: Context, value: Int) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("swipe_distance", value.coerceIn(12, 60)).apply()
    private fun restart(context: Context) = refresh(context)
    fun verticalPosition(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("vertical_position", .48f)

    fun setVerticalPosition(context: Context, position: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("vertical_position", position.coerceIn(.05f, .9f)).apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        if (enabled) ContextCompat.startForegroundService(context, Intent(context, EdgeBarService::class.java))
        else context.stopService(Intent(context, EdgeBarService::class.java))
    }

    fun setRightSide(context: Context, right: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("right_side", right).apply()
        restart(context)
    }
}
