package com.astroboii47.commander

import android.content.Context
import android.util.Log

/**
 * Android bridge between existing providers and the pure unified ranking core.
 *
 * Reuses integrations without rewriting them: [AppCatalog], [SystemSettingsCatalog],
 * [ControlActions], [AppSearchTerms], [AppLaunchHistory] and [AppUsageRanker] stay
 * the source of truth. This object only converts their hits to [RankCandidate]s,
 * builds [RankingSignals] from local history + usage, and records selections.
 */
object UnifiedSearchEngine {
    private const val TAG = "HoustonSearch"

    /** Build app candidates from the cached catalog, reusing custom search terms. */
    fun appCandidates(context: Context, query: String, limit: Int = 50): List<RankCandidate> {
        val appContext = context.applicationContext
        val customTerms = try {
            AppSearchTerms.aliases(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "aliases unavailable", e)
            emptyMap()
        }
        return try {
            AppCatalog.search(appContext, query.ifBlank { "" }, limit).map { entry ->
                UnifiedAggregator.appCandidate(
                    entry.label,
                    entry.packageName,
                    customTerms[entry.packageName].orEmpty(),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "appCandidates failed for query len=${query.length}", e)
            emptyList()
        }
    }

    fun settingCandidates(query: String): List<RankCandidate> = try {
        SystemSettingsCatalog.search(query).map {
            UnifiedAggregator.settingCandidate(it.label, it.subtitle, it.action, it.keywords)
        }
    } catch (e: Exception) {
        Log.w(TAG, "settingCandidates failed", e)
        emptyList()
    }

    fun controlCandidates(query: String): List<RankCandidate> = try {
        ControlActions.results(query).map {
            UnifiedAggregator.controlCandidate(it.label, it.subtitle, it.kind.name)
        }
    } catch (e: Exception) {
        Log.w(TAG, "controlCandidates failed", e)
        emptyList()
    }

    /** Fast local candidates: apps + settings + controls + intent-driven commands. No I/O. */
    fun fastCandidates(
        context: Context,
        query: String,
        intent: SearchIntent = SearchIntent.Plain,
    ): List<RankCandidate> {
        val out = ArrayList<RankCandidate>()
        out += appCandidates(context, query, 30)
        out += settingCandidates(query)
        out += controlCandidates(query)
        when (intent) {
            is SearchIntent.Calculator -> {
                // Preview is computed by the caller (needs UnitConverter/ExpressionParser);
                // keep a placeholder candidate so the command row can still rank.
                out += UnifiedAggregator.calculatorCandidate(intent.expression, intent.expression)
            }
            is SearchIntent.Timer -> {
                out += UnifiedAggregator.timerCandidate(intent.seconds, intent.label)
            }
            is SearchIntent.Downloads -> {
                out += RankCandidate(
                    id = "file:downloads-folder",
                    title = "Downloads",
                    subtitle = "folder · internal storage",
                    source = UnifiedSource.FOLDER,
                    aliases = listOf("downloads", "download"),
                )
            }
            is SearchIntent.WebService -> {
                out += UnifiedAggregator.aliasCandidate(
                    intent.targetId,
                    AliasSettings.targets.firstOrNull { it.id == intent.targetId }?.label ?: intent.targetId,
                    intent.query,
                )
            }
            else -> Unit
        }
        if (query.isNotBlank()) out += UnifiedAggregator.webCandidate(query.trim())
        return out
    }

    /** Normalized 0..1 usage weights for [ids] of the form "app:<package>". */
    fun usageWeights(context: Context, ids: Collection<String>): Map<String, Double> {
        val packages = ids.mapNotNull { id -> if (id.startsWith("app:")) id.removePrefix("app:") else null }
        if (packages.isEmpty()) return emptyMap()
        return try {
            val appContext = context.applicationContext
            val launchOrder = AppLaunchHistory.packages(appContext)
            val launchWeight = packages.associateWith { pkg ->
                val index = launchOrder.indexOf(pkg)
                if (index < 0) 0.0 else (launchOrder.size - index).toDouble() / launchOrder.size.coerceAtLeast(1)
            }
            if (!AppUsageRanker.hasAccess(appContext)) {
                return launchWeight.filterValues { it > 0 }.mapValues { it.value * 0.7 }
            }
            val manager = appContext.getSystemService(android.app.usage.UsageStatsManager::class.java)
                ?: return launchWeight
            val end = System.currentTimeMillis()
            val stats = try {
                manager.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    end - 14L * 24 * 60 * 60 * 1000,
                    end,
                ).orEmpty().groupBy { it.packageName }
            } catch (e: Exception) {
                Log.w(TAG, "queryUsageStats failed", e)
                emptyMap()
            }
            val foreground = packages.associateWith { pkg ->
                stats[pkg].orEmpty().sumOf { it.totalTimeInForeground }.toDouble()
            }
            val maxForeground = (foreground.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val combined = HashMap<String, Double>()
            for (pkg in packages) {
                val fg = (foreground[pkg] ?: 0.0) / maxForeground
                val launch = launchWeight[pkg] ?: 0.0
                combined["app:$pkg"] = (fg * 0.8 + launch * 0.2).coerceIn(0.0, 1.0)
            }
            combined.filterValues { it > 0.01 }
        } catch (e: Exception) {
            Log.w(TAG, "usageWeights failed", e)
            emptyMap()
        }
    }

    /**
     * Signals for [candidates], merging new unified keys with legacy Native keys
     * ("App:<package>", "Setting:<title>", ...) so existing learned ranking is kept.
     */
    fun signals(context: Context, candidates: List<RankCandidate>): RankingSignals {
        return try {
            val ids = candidates.map { it.id }
            val usage = usageWeights(context, ids)
            val unified = CommandBarRanker.signals(context, ids, ::unifiedKey)
            val mergedCounts = HashMap(unified.counts)
            val mergedTimes = HashMap(unified.lastSelectedAtMillis)
            for (candidate in candidates) {
                for ((kind, identity) in legacyKeys(candidate)) {
                    val count = CommandBarRanker.score(context, kind, identity)
                    if (count > (mergedCounts[candidate.id] ?: 0)) mergedCounts[candidate.id] = count
                    val time = CommandBarRanker.lastSelectedAt(context, kind, identity)
                    if (time > (mergedTimes[candidate.id] ?: 0L)) mergedTimes[candidate.id] = time
                }
            }
            RankingSignals(mergedCounts, mergedTimes, System.currentTimeMillis(), usage)
        } catch (e: Exception) {
            Log.w(TAG, "signals failed, returning empty", e)
            RankingSignals()
        }
    }

    private fun unifiedKey(id: String): Pair<String, String>? {
        val source = id.substringBefore(':').uppercase()
        return try {
            UnifiedSource.valueOf(source)
            source to id
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun legacyKeys(candidate: RankCandidate): List<Pair<String, String>> = when (candidate.source) {
        UnifiedSource.APP -> listOf("App" to candidate.id.removePrefix("app:"))
        UnifiedSource.SETTING -> listOf("Setting" to candidate.title)
        UnifiedSource.CONTROL -> listOf("Control" to candidate.title)
        UnifiedSource.FILE, UnifiedSource.FOLDER -> listOf(
            "File" to candidate.id.removePrefix("file:"),
        )
        UnifiedSource.WEB -> listOf("Web" to candidate.title)
        UnifiedSource.COMMAND -> emptyList()
        else -> emptyList()
    }

    /** Record a selection for future ranking. Dual-writes legacy keys during migration. */
    fun recordSelection(context: Context, candidate: RankCandidate) {
        try {
            val sourceKey = candidate.source.name
            CommandBarRanker.record(context, sourceKey, candidate.id)
            for ((kind, identity) in legacyKeys(candidate)) {
                CommandBarRanker.record(context, kind, identity)
            }
        } catch (e: Exception) {
            Log.w(TAG, "recordSelection failed for ${candidate.id}", e)
        }
    }

    /** Rank fast local candidates for a plain (trigger-free) query. */
    fun rankPlain(
        context: Context,
        query: String,
        extra: List<RankCandidate> = emptyList(),
        intent: SearchIntent = SearchIntent.Plain,
        limit: Int = 20,
    ): List<ScoredCandidate> {
        val candidates = (fastCandidates(context, query, intent) + extra).distinctBy { it.id }
        val signals = signals(context, candidates)
        // Calculator/timer/web-alias intents are executable commands: keep them near
        // the top when their text matches, without hiding navigational results.
        return UnifiedAggregator.rank(query, candidates, signals, intent, limit = limit)
    }
}
