package com.astroboii47.commander

import android.content.Context
import org.json.JSONObject

/** Learns from activated command bar results, across all result types. Local only. */
object CommandBarRanker {
    private const val PREFS = "command_bar_ranking"
    private fun key(kind: String, identity: String) = "$kind:$identity"
    private fun readCounts(context: Context): JSONObject = runCatching {
        JSONObject(context.getSharedPreferences(PREFS, 0).getString("counts", "{}"))
    }.getOrDefault(JSONObject())
    private fun readTimes(context: Context): JSONObject = runCatching {
        JSONObject(context.getSharedPreferences(PREFS, 0).getString("times", "{}"))
    }.getOrDefault(JSONObject())
    fun record(context: Context, kind: String, identity: String) {
        val counts = readCounts(context)
        val key = key(kind, identity)
        counts.put(key, (counts.optInt(key) + 1).coerceAtMost(1000))
        val times = readTimes(context)
        times.put(key, System.currentTimeMillis())
        // Prune timestamps for identities that no longer have counts, keeping prefs small.
        val keys = HashSet<String>()
        val countNames = counts.keys()
        while (countNames.hasNext()) keys.add(countNames.next())
        val timeNames = times.keys()
        val stale = ArrayList<String>()
        while (timeNames.hasNext()) {
            val name = timeNames.next()
            if (name !in keys) stale.add(name)
        }
        stale.forEach(times::remove)
        context.getSharedPreferences(PREFS, 0).edit()
            .putString("counts", counts.toString())
            .putString("times", times.toString())
            .apply()
    }
    fun score(context: Context, kind: String, identity: String) = readCounts(context).optInt(key(kind, identity))
    fun lastSelectedAt(context: Context, kind: String, identity: String): Long =
        readTimes(context).optLong(key(kind, identity))

    /**
     * Unified ranking signals for a batch of stable [RankCandidate.id] values.
     * [kindForId] maps each candidate id back to the (kind, identity) pair used
     * when recording, so existing learned counts keep working unchanged.
     */
    fun signals(
        context: Context,
        ids: Collection<String>,
        kindForId: (String) -> Pair<String, String>?,
        usage: Map<String, Double> = emptyMap(),
    ): RankingSignals {
        val counts = HashMap<String, Int>()
        val times = HashMap<String, Long>()
        val storedCounts = readCounts(context)
        val storedTimes = readTimes(context)
        for (id in ids) {
            val mapped = kindForId(id) ?: continue
            val storageKey = key(mapped.first, mapped.second)
            storedCounts.optInt(storageKey).takeIf { it > 0 }?.let { counts[id] = it }
            storedTimes.optLong(storageKey).takeIf { it > 0L }?.let { times[id] = it }
        }
        return RankingSignals(counts, times, System.currentTimeMillis(), usage)
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, 0).edit().clear().apply()
}
