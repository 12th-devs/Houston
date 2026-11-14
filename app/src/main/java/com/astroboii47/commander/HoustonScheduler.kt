package com.astroboii47.commander

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

/**
 * Best-effort background scheduling with framework APIs only
 * (no exact-timing promises, honoring battery restrictions):
 * a daily morning-briefing alarm, rescheduled on boot/update/unlock.
 * Automation rules are evaluated on app start and on each alarm fire;
 * all runs are recorded in [AutomationEngine].
 */
object HoustonScheduler {
    private const val REQ_BRIEFING = 3101
    private const val PREFS = "houston_scheduler"
    private const val KEY_LAST_BRIEFING = "last_briefing_day"

    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = briefingIntent(context) ?: return
        // Next 7:30 local (or the rule's scheduled time if customized).
        val time = AutomationEngine.loadRules(context)
            .firstOrNull { it.trigger == AutoTrigger.SCHEDULED_TIME && it.enabled }
            ?.timeIso?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.of(7, 30)
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, time.hour)
            set(Calendar.MINUTE, time.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        briefingIntent(context)?.let { runCatching { am.cancel(it) } }
    }

    // ---------- Periodic background sync ----------

    private const val REQ_SYNC = 3102

    fun rescheduleSync(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val existing = syncIntent(context)
        if (!HoustonSyncPrefs.syncEnabled(context)) {
            existing?.let { runCatching { am.cancel(it) } }
            return
        }
        val pi = existing ?: return
        val delayMs = (HoustonSyncPrefs.intervalHours(context) + HoustonSyncPrefs.backoffHours(context)) * 3_600_000L
        val at = System.currentTimeMillis() + delayMs.coerceAtLeast(2 * 3_600_000L)
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun syncIntent(context: Context): PendingIntent? {
        val intent = Intent(context, SyncReceiver::class.java).setAction(HoustonSyncService.ACTION_SYNC)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return runCatching { PendingIntent.getBroadcast(context, REQ_SYNC, intent, flags) }.getOrNull()
    }

    private fun briefingIntent(context: Context): PendingIntent? {
        val intent = Intent(context, BriefingReceiver::class.java).setAction("com.astroboii47.commander.MORNING_BRIEFING")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return runCatching { PendingIntent.getBroadcast(context, REQ_BRIEFING, intent, flags) }.getOrNull()
    }

    /** Builds the morning briefing from cached data and posts one notification. */
    fun fireBriefing(context: Context) {
        reschedule(context) // one-shot: always queue the next day
        val today = LocalDate.now().toString()
        if (SettingsStore.getString(context, PREFS, KEY_LAST_BRIEFING) == today) return
        val courses = PowerSchoolStore.loadCourses(context)
        val goals = SwimPlatformStore.loadGoals(context)
        val results = SwimPlatformStore.loadResults(context)
        val memories = MemoryStore.loadMemories(context)
        val calLines = runCatching {
            val day = LocalDate.now()
            ICalStore.occurrences(context, day, day).take(3).map { it.summary }
        }.getOrDefault(emptyList()) +
            PowerSchoolStore.dueOn(courses, LocalDate.now()).take(2).map { "${it.name} (${it.courseName})" }
        val schoolLines = PowerSchoolStore.upcoming(courses, LocalDate.now(), 3).take(2).map {
            "${it.name} due ${it.dueIso ?: "soon"}"
        }
        val swimLines = goals.filter { !it.completed }.take(1).map {
            "Goal: ${it.label} → ${formatSwimTime(it.targetSec)}"
        } + results.take(1).map { "Latest: ${it.eventName} ${it.timeSec?.let { t -> formatSwimTime(t) } ?: ""}".trim() }
        val memLines = memories.take(1).map { (it.title.ifBlank { it.body.take(50) }).take(80) }
        if (calLines.isEmpty() && schoolLines.isEmpty() && swimLines.isEmpty() && memLines.isEmpty()) return
        SettingsStore.edit(context, PREFS) { putString(KEY_LAST_BRIEFING, today) }
        val briefing = buildDailyBriefing(
            today = LocalDate.now(), calendarLines = calLines,
            schoolLines = schoolLines, swimLines = swimLines, memoryLines = memLines,
        )
        InsightStore.ingest(
            context,
            listOf(
                Insight(
                    id = "briefing:morning:$today",
                    kind = InsightKind.BRIEFING,
                    title = briefing.greeting,
                    explanation = briefing.sections.joinToString(" ") { s -> s.heading + ": " + s.lines.take(2).joinToString("; ") }.take(400),
                    refs = emptyList(),
                    score = 70,
                    expiresIso = LocalDate.now().plusDays(1).toString() + "T00:00:00Z",
                    actionLabel = "Open Houston",
                    actionTarget = "home",
                ),
            ),
        )
        postBriefingNotification(context, briefing)
        // Evaluate automation rules against the fresh insight set.
        val rules = AutomationEngine.loadRules(context)
        val fired = AutomationEngine.evaluate(rules, emptyList(), InsightStore.load(context))
        val now = java.time.Instant.now().toString()
        for ((rule, reason) in fired) {
            AutomationEngine.recordRun(context, AutomationRun(rule.id, now, true, "alarm: $reason"))
        }
    }

    private fun postBriefingNotification(context: Context, briefing: DailyBriefing) {
        if (!HoustonNotifyPrefs.enabled(context, HoustonNotifyPrefs.CH_BRIEFING)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        InsightNotifier.ensureChannels(context)
        val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 3102,
            Intent(context, DashboardActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = briefing.sections.joinToString("\n") { s -> s.heading + ": " + s.lines.take(2).joinToString("; ") }.take(500)
        val n = NotificationCompat.Builder(context, HoustonNotifyPrefs.CH_BRIEFING)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(briefing.greeting)
            .setContentText(text.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify("houston_briefing", todayHash(), n) }
    }

    private fun todayHash(): Int = LocalDate.now().toString().hashCode()
}

/** Alarm + boot entry point for the morning briefing. Lightweight by design. */
class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != null && intent.action != "com.astroboii47.commander.MORNING_BRIEFING") return
        runCatching { HoustonScheduler.fireBriefing(context) }
    }
}
