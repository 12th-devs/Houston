package com.astroboii47.commander

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** Posts insights to granular channels; never repeats acked insights. */
object InsightNotifier {
    private const val GROUP = "houston_insights"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        fun ch(id: String, name: String, desc: String) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = desc
                setShowBadge(true)
            }
        nm.createNotificationChannels(
            listOf(
                ch(HoustonNotifyPrefs.CH_BRIEFING, "Briefings", "Daily briefings and weekly reviews."),
                ch(HoustonNotifyPrefs.CH_SCHOOL, "School", "Grade changes, new and missing assignments, deadlines."),
                ch(HoustonNotifyPrefs.CH_SWIM, "Swimming", "Personal bests, goal completions, new results."),
                ch(HoustonNotifyPrefs.CH_MEMORY, "Memories", "Capture and analysis updates."),
                ch(HoustonNotifyPrefs.CH_AUTO, "Automations", "Automation runs and reminders."),
            ),
        )
    }

    fun channelFor(kind: InsightKind): String = when (kind) {
        InsightKind.SCHOOL -> HoustonNotifyPrefs.CH_SCHOOL
        InsightKind.SWIM -> HoustonNotifyPrefs.CH_SWIM
        InsightKind.MEMORY -> HoustonNotifyPrefs.CH_MEMORY
        InsightKind.BRIEFING, InsightKind.WEEKLY -> HoustonNotifyPrefs.CH_BRIEFING
        else -> HoustonNotifyPrefs.CH_AUTO
    }

    fun notifyNew(context: Context, insights: List<Insight>) {
        if (insights.isEmpty()) return
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        for (s in insights.take(5)) {
            val channel = channelFor(s.kind)
            if (!HoustonNotifyPrefs.enabled(context, channel)) continue
            if (s.acked) continue
            val open = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("insight_target", s.actionTarget ?: s.id)
            }
            val pi = PendingIntent.getActivity(
                context, s.id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(s.title)
                .setContentText(s.explanation.take(200))
                .setStyle(NotificationCompat.BigTextStyle().bigText(s.explanation))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            nm.notify("houston_insight", s.id.hashCode(), n)
        }
    }
}
