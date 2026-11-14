package com.astroboii47.commander

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object AppUsageRanker {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun ranked(context: Context, apps: List<AppResult>): List<AppResult> {
        val fallback = AppLaunchHistory.packages(context).withIndex().associate { it.value to (10 - it.index) }
        if (!hasAccess(context)) return apps.sortedWith(compareByDescending<AppResult> { fallback[it.packageName] ?: 0 }.thenBy { it.label })
        val end = System.currentTimeMillis()
        val stats = context.getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 14L * 24 * 60 * 60 * 1000, end)
            .orEmpty().groupBy { it.packageName }
        return apps.sortedWith(compareByDescending<AppResult> { app ->
            stats[app.packageName].orEmpty().sumOf { it.totalTimeInForeground } +
                (fallback[app.packageName] ?: 0) * 60_000L
        }.thenBy { it.label })
    }
}
