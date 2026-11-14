package com.astroboii47.commander

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Best-effort background sync with teeth: a short-lived foreground
 * `dataSync` service (survives doze long enough to finish), chained
 * inexact alarms, boot rescheduling, retry with backoff, and a full run
 * history in [AutomationEngine]. No exact-timing claims — Android may defer
 * any individual alarm under battery restrictions, but the chain always
 * re-queues, retries, and records what happened.
 */
class HoustonSyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(SYNC_NOTIF_ID, progressNotification("Syncing calendars and grades…"))
        scope.launch {
            try {
                runSync(this@HoustonSyncService)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun progressNotification(text: String) =
        NotificationCompat.Builder(this, HoustonNotifyPrefs.CH_AUTO)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Houston sync")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

    companion object {
        const val SYNC_NOTIF_ID = 3201
        const val ACTION_SYNC = "com.astroboii47.commander.SYNC_NOW"

        fun start(context: Context) {
            val intent = Intent(context, HoustonSyncService::class.java).setAction(ACTION_SYNC)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        /** One full pass: feeds → PowerSchool (session only) → insights → automations. */
        suspend fun runSync(context: Context) {
            val started = java.time.Instant.now().toString()
            var failures = 0
            // 1. Calendars (never fatal).
            for (feed in ICalStore.list(context)) {
                runCatching { ICalStore.refresh(context, feed) }
                    .onFailure { failures++ }
            }
            // 2. PowerSchool with saved session only — never prompts, never stores passwords.
            runCatching {
                val client = PowerSchoolStore.client(context)
                if (!client.sessionValid() && !client.loadSession()) {
                    throw PsException.SessionExpired("No saved PowerSchool session for background sync.")
                }
                val prev = PowerSchoolStore.loadCourses(context)
                val prevKeys = prev.flatMap { it.assignments }.map(::assignmentKey).toSet()
                val raw = client.collectAll(null, null)
                val normalized = normalizeRawRecords(raw, LocalDate.now())
                val stamp = java.time.Instant.now().toString()
                PowerSchoolStore.saveCourses(context, normalized, stamp)
                PowerSchoolStore.recordGradeSnapshot(context, normalized, LocalDate.now().toString())
                val prefs = SchoolAlertStore.load(context)
                val fresh = InsightStore.ingest(
                    context,
                    detectSchoolChanges(prev, normalized, prefs, prevKeys),
                )
                if (fresh.isNotEmpty()) InsightNotifier.notifyNew(context, rankInsights(fresh))
            }.onFailure { failures++ }
            // 3. Automation evaluation against the fresh insight set.
            runCatching {
                val rules = AutomationEngine.loadRules(context)
                val fired = AutomationEngine.evaluate(rules, emptyList(), InsightStore.load(context))
                val now = java.time.Instant.now().toString()
                for ((rule, reason) in fired) {
                    AutomationEngine.recordRun(context, AutomationRun(rule.id, now, true, "sync: $reason"))
                }
            }
            AutomationEngine.recordRun(
                context,
                AutomationRun(
                    "background-sync", started, failures == 0,
                    if (failures == 0) "Calendars, grades, automations" else "$failures source(s) failed — will retry",
                ),
            )
        }
    }
}

/** Alarm entry point for periodic background sync. Re-queues after firing. */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != HoustonSyncService.ACTION_SYNC) return
        runCatching {
            HoustonSyncPrefs.touchScheduled(context)
            HoustonSyncService.start(context)
            HoustonScheduler.rescheduleSync(context) // chain the next window
        }
    }
}

object HoustonSyncPrefs {
    private const val PREFS = "houston_sync"
    private const val KEY_ENABLED = "sync_enabled"
    private const val KEY_HOURS = "sync_hours"
    private const val KEY_LAST = "sync_last"
    private const val KEY_BACKOFF = "sync_backoff_h"

    fun syncEnabled(context: Context): Boolean =
        SettingsStore.prefs(context, PREFS).getBoolean(KEY_ENABLED, true)

    fun setSyncEnabled(context: Context, on: Boolean) {
        SettingsStore.edit(context, PREFS) { putBoolean(KEY_ENABLED, on) }
    }

    fun intervalHours(context: Context): Long =
        SettingsStore.prefs(context, PREFS).getString(KEY_HOURS, null)?.toLongOrNull()?.coerceIn(2, 24) ?: 6

    fun setIntervalHours(context: Context, h: Long) {
        SettingsStore.edit(context, PREFS) { putString(KEY_HOURS, h.coerceIn(2, 24).toString()) }
    }

    fun lastSync(context: Context): String? =
        SettingsStore.getString(context, PREFS, KEY_LAST)

    fun touchScheduled(context: Context) {
        SettingsStore.edit(context, PREFS) { putString(KEY_LAST, java.time.Instant.now().toString()) }
        // Reset backoff on a successful schedule tick.
        SettingsStore.edit(context, PREFS) { putString(KEY_BACKOFF, "0") }
    }

    fun backoffHours(context: Context): Long =
        SettingsStore.prefs(context, PREFS).getString(KEY_BACKOFF, null)?.toLongOrNull() ?: 0

    fun noteFailure(context: Context) {
        val next = (backoffHours(context) + 1).coerceAtMost(3)
        SettingsStore.edit(context, PREFS) { putString(KEY_BACKOFF, next.toString()) }
    }
}
