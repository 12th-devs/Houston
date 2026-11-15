package com.astroboii47.commander.secondbrain.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.astroboii47.commander.HoustonSyncService
import com.astroboii47.commander.ICalStore
import com.astroboii47.commander.Insight
import com.astroboii47.commander.InsightKind
import com.astroboii47.commander.InsightNotifier
import com.astroboii47.commander.InsightStore
import com.astroboii47.commander.PowerSchoolStore
import com.astroboii47.commander.secondbrain.context.BrainContext
import com.astroboii47.commander.secondbrain.context.ContextItem
import com.astroboii47.commander.secondbrain.db.AgentRun
import com.astroboii47.commander.secondbrain.di.BrainGraph
import com.astroboii47.commander.secondbrain.di.BrainPrefs
import com.astroboii47.commander.plan.DayBlock
import com.astroboii47.commander.plan.Planner
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Deferrable background intelligence: reuse the foreground sync pass, then
 * run a proactive agent evaluation (today/tomorrow conflicts, upcoming load)
 * and notify only when the relevance bar is met and quiet hours allow.
 * Replaces nothing — the inexact alarms stay for the morning briefing slot.
 */
class BrainWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!BrainPrefs.proactive(context)) return Result.success()
        runCatching { HoustonSyncService.runSync(context) }
        return runCatching {
            val snapshot = BrainContext.snapshot(context)
            val today = LocalDate.now()
            val occurrences = runCatching { ICalStore.occurrences(context, today, today.plusDays(1)) }.getOrDefault(emptyList())
            val timed = occurrences.mapNotNull { o ->
                val st = o.startTime ?: return@mapNotNull null
                val en = o.endTime ?: st.plusHours(1)
                DayBlock(st.hour * 60 + st.minute, en.hour * 60 + en.minute, o.summary, "commitment", o.feedName)
            }
            val fresh = evaluateProactive(today, timed, snapshot.upcoming)
            val posted = if (fresh.isNotEmpty()) {
                val level = BrainPrefs.notifyLevel(context)
                val gated = if (level == "none") emptyList() else fresh.filter { level == "all" || it.score >= 75 }
                if (gated.isNotEmpty() && !inQuietHours(context)) {
                    InsightStore.ingest(context, gated)
                    InsightNotifier.notifyNew(context, gated)
                } else if (gated.isNotEmpty()) {
                    // Quiet hours: store, don't notify.
                    InsightStore.ingest(context, gated)
                }
                gated
            } else emptyList()
            BrainGraph.db(context).runs().upsert(
                AgentRun(
                    trigger = "proactive",
                    status = "done",
                    finishedAt = System.currentTimeMillis(),
                    summary = "Proactive pass: ${posted.size} surfaced, ${snapshot.today.size} today, ${snapshot.upcoming.size} upcoming.",
                ),
            )
            Result.success()
        }.getOrElse {
            BrainGraph.db(context).runs().upsert(
                AgentRun(trigger = "proactive", status = "failed", finishedAt = System.currentTimeMillis(), summary = (it.message ?: "error").take(200)),
            )
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE = "brain-proactive-sync"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BrainWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .addTag(UNIQUE)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE)
        }

        suspend fun inQuietHours(context: Context): Boolean {
            val (start, end) = BrainPrefs.quietWindow(context)
            val now = LocalTime.now().hour * 60 + LocalTime.now().minute
            return if (start <= end) now in start until end else (now >= start || now < end)
        }

        /**
         * Pure evaluation over today's timed blocks and upcoming items:
         * conflicts, overloaded days, and due-tomorrow pile-ups. No I/O,
         * fully unit-tested.
         */
        fun evaluateProactive(
            today: LocalDate,
            timed: List<DayBlock>,
            upcoming: List<ContextItem>,
        ): List<Insight> {
            val out = mutableListOf<Insight>()
            val conflicts = Planner.detectConflicts(timed)
            if (conflicts.isNotEmpty()) {
                val c = conflicts.first()
                out.add(
                    Insight(
                        id = "proactive.conflict:${today}",
                        kind = InsightKind.CALENDAR,
                        title = "Schedule conflict: ${c.a.title} × ${c.b.title}",
                        explanation = "They overlap by ${c.overlapMin} min today. Want a re-plan?",
                        score = 88,
                        actionLabel = "Open plan",
                        actionTarget = "plan",
                    ),
                )
            }
            val freeToday = Planner.freeBlocks(timed).sumOf { it.endMin - it.startMin }
            val committed = timed.sumOf { it.endMin - it.startMin }
            val load = Planner.overloadScore(committed, freeToday)
            if (load >= 0.85 && timed.isNotEmpty()) {
                out.add(
                    Insight(
                        id = "proactive.overload:${today}",
                        kind = InsightKind.CALENDAR,
                        title = "Today is overloaded",
                        explanation = Planner.overloadLabel(load) + " ${freeToday} min open.",
                        score = 78,
                        actionLabel = "Open plan",
                        actionTarget = "plan",
                    ),
                )
            }
            val dueTomorrow = upcoming.filter { it.dateIso == today.plusDays(1).toString() && it.domain == "school" }
            if (dueTomorrow.size >= 2) {
                out.add(
                    Insight(
                        id = "proactive.duetomorrow:${today}",
                        kind = InsightKind.SCHOOL,
                        title = "${dueTomorrow.size} assignments due tomorrow",
                        explanation = dueTomorrow.take(3).joinToString("; ") { it.title } + ". Want study blocks today?",
                        score = 82,
                        actionLabel = "Open plan",
                        actionTarget = "plan",
                    ),
                )
            }
            return out.take(3)
        }
    }
}
