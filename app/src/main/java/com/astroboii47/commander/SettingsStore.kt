package com.astroboii47.commander

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Single entry point for SharedPreferences.
 *
 * Houston currently calls `getSharedPreferences()` in ~30 settings objects
 * with stringly-typed file names. This wrapper keeps the same file names
 * (no migration needed) but centralises access so logging, key validation,
 * and a future DataStore move happen in one place.
 *
 * New code should use [prefs] / [edit]. Existing `*Settings` objects can be
 * migrated one file at a time.
 */
object SettingsStore {
    private const val TAG = "HoustonSettings"

    fun prefs(context: Context, name: String): SharedPreferences {
        require(name.isNotBlank()) { "prefs name must not be blank" }
        return try {
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "prefs($name) failed, using empty fallback", e)
            throw e
        }
    }

    fun edit(context: Context, name: String, block: SharedPreferences.Editor.() -> Unit) {
        try {
            prefs(context, name).edit().apply(block).apply()
        } catch (e: Exception) {
            Log.w(TAG, "edit($name) failed", e)
        }
    }

    fun getString(context: Context, file: String, key: String, default: String? = null): String? {
        return runCatching { prefs(context, file).getString(key, default) }.getOrElse {
            Log.w(TAG, "getString($file/$key) failed", it)
            default
        }
    }
}
