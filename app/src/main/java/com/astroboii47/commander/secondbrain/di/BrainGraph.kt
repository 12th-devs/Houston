package com.astroboii47.commander.secondbrain.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.astroboii47.commander.agent.AgentEngine
import com.astroboii47.commander.memory.BrainMemory
import com.astroboii47.commander.plan.Planner
import com.astroboii47.commander.secondbrain.db.SecondBrainDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.brainDataStore: DataStore<Preferences> by preferencesDataStore("brain_prefs")

/**
 * Manual dependency graph (consistent with the project's no-DI convention).
 * Room for structured persistence, DataStore for preferences, singletons
 * keyed off the application context.
 */
object BrainGraph {
    fun db(context: Context): SecondBrainDb = SecondBrainDb.get(context)

    fun memory(context: Context): BrainMemory = BrainMemory(db(context))

    fun agent(context: Context): AgentEngine = AgentEngine(db(context))

    fun planner(): Planner = Planner
}

object BrainPrefs {
    private val PROACTIVE = booleanPreferencesKey("proactive_enabled")
    private val QUIET_START = intPreferencesKey("quiet_start_min")
    private val QUIET_END = intPreferencesKey("quiet_end_min")
    private val MODEL_CALLS = longPreferencesKey("model_calls_30d")
    private val EST_INPUT_CHARS = longPreferencesKey("est_input_chars_30d")
    private val EST_OUTPUT_CHARS = longPreferencesKey("est_output_chars_30d")
    private val LAST_RESET = longPreferencesKey("usage_window_start")
    private val NOTIFY_LEVEL = stringPreferencesKey("notify_level") // all|important|none

    suspend fun proactive(context: Context): Boolean =
        context.brainDataStore.data.map { it[PROACTIVE] ?: true }.first()

    suspend fun setProactive(context: Context, on: Boolean) {
        context.brainDataStore.edit { it[PROACTIVE] = on }
    }

    suspend fun quietWindow(context: Context): Pair<Int, Int> =
        context.brainDataStore.data.map { (it[QUIET_START] ?: 1380) to (it[QUIET_END] ?: 420) }.first()

    suspend fun setQuietWindow(context: Context, startMin: Int, endMin: Int) {
        context.brainDataStore.edit { it[QUIET_START] = startMin; it[QUIET_END] = endMin }
    }

    suspend fun notifyLevel(context: Context): String =
        context.brainDataStore.data.map { it[NOTIFY_LEVEL] ?: "important" }.first()

    suspend fun setNotifyLevel(context: Context, level: String) {
        context.brainDataStore.edit { it[NOTIFY_LEVEL] = level }
    }

    /** Token/cost accounting: ~4 chars per token, per-provider $/1K kept in code. */
    suspend fun recordUsage(context: Context, inputChars: Int, outputChars: Int) {
        context.brainDataStore.edit {
            val now = System.currentTimeMillis()
            val start = it[LAST_RESET] ?: now
            if (now - start > 30L * 24 * 60 * 60 * 1000) {
                it[LAST_RESET] = now
                it[MODEL_CALLS] = 1
                it[EST_INPUT_CHARS] = inputChars.toLong()
                it[EST_OUTPUT_CHARS] = outputChars.toLong()
            } else {
                it[MODEL_CALLS] = (it[MODEL_CALLS] ?: 0) + 1
                it[EST_INPUT_CHARS] = (it[EST_INPUT_CHARS] ?: 0) + inputChars
                it[EST_OUTPUT_CHARS] = (it[EST_OUTPUT_CHARS] ?: 0) + outputChars
            }
        }
    }

    suspend fun usageSnapshot(context: Context): Triple<Long, Long, Long> =
        context.brainDataStore.data.map {
            Triple(it[MODEL_CALLS] ?: 0, it[EST_INPUT_CHARS] ?: 0, it[EST_OUTPUT_CHARS] ?: 0)
        }.first()

    /** Rough USD estimate for the rolling window (conservative blended rate). */
    fun estimateUsd(inputChars: Long, outputChars: Long): Double {
        val inTokens = inputChars / 4.0
        val outTokens = outputChars / 4.0
        return (inTokens * 0.35 + outTokens * 1.05) / 1_000_000.0
    }
}
