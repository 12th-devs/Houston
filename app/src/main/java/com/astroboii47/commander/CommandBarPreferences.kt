package com.astroboii47.commander

import android.content.Context

object CommandBarPreferences {
    private const val NAME = "command_bar_preferences"
    fun solid(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("solid", false)
    fun setSolid(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("solid", value).apply()
    fun accent(context: Context) = context.getSharedPreferences(NAME, 0).getInt("accent", 0)
    fun setAccent(context: Context, value: Int) = context.getSharedPreferences(NAME, 0).edit().putInt("accent", value).apply()
    fun compact(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("compact", false)
    fun setCompact(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("compact", value).apply()
    fun searchScreenshots(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("search_screenshots", true)
    fun setSearchScreenshots(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("search_screenshots", value).apply()
    fun searchFiles(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("search_files", true)
    fun setSearchFiles(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("search_files", value).apply()
    fun showHistory(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("show_history", true)
    fun setShowHistory(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("show_history", value).apply()
    fun resultCount(context: Context) = context.getSharedPreferences(NAME, 0).getInt("result_count", 12).coerceIn(6, 20)
    fun setResultCount(context: Context, value: Int) = context.getSharedPreferences(NAME, 0).edit().putInt("result_count", value.coerceIn(6, 20)).apply()
    fun showKeyboard(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("show_keyboard", true)
    fun setShowKeyboard(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("show_keyboard", value).apply()
    fun theme(context: Context) = context.getSharedPreferences(NAME, 0).getInt("theme", 0).coerceIn(0, 3)
    fun setTheme(context: Context, value: Int) = context.getSharedPreferences(NAME, 0).edit().putInt("theme", value.coerceIn(0, 3)).apply()
    fun materialYouBar(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("material_you_bar", true)
    fun setMaterialYouBar(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("material_you_bar", value).apply()
    fun heightPercent(context: Context) = context.getSharedPreferences(NAME, 0).getInt("height_percent", 38).coerceIn(22, 70)
    fun setHeightPercent(context: Context, value: Int) = context.getSharedPreferences(NAME, 0).edit().putInt("height_percent", value.coerceIn(22, 70)).apply()
    fun miniPlayer(context: Context) = context.getSharedPreferences(NAME, 0).getBoolean("mini_player", false)
    fun setMiniPlayer(context: Context, value: Boolean) = context.getSharedPreferences(NAME, 0).edit().putBoolean("mini_player", value).apply()
}
