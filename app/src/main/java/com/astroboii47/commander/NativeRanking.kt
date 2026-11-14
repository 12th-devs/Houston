package com.astroboii47.commander

internal enum class NativeKind {
    App, File, Setting, Calculator, Web, Command, BrowseFiles, FileAccess, OpenSettings,
    ScreenshotMenu, ScreenshotAccess, Screenshot, MusicMenu, MusicPrevious, MusicToggle, MusicNext, MusicOpen,
    ClipboardMenu, Clipboard, SearchHistory, DownloadsMenu, CommanderSetting, AiMenu, AiPrompt, Weather, Brightness, Volume, ScreenshotCapture, BluetoothToggle, BluetoothSettings, BluetoothDevice, BluetoothAccess,
}

internal data class NativeResult(
    val title: String,
    val subtitle: String = "",
    val kind: NativeKind,
    val app: AppResult? = null,
    val file: FileResult? = null,
    val setting: SystemSettingResult? = null,
    val screenshot: ScreenshotEntry? = null,
    val clipboard: String? = null,
) {
    fun rankIdentity(): String = when (kind) {
        NativeKind.MusicPrevious, NativeKind.MusicToggle, NativeKind.MusicNext, NativeKind.MusicOpen -> kind.name
        else -> app?.packageName ?: file?.uri?.toString() ?: screenshot?.uri?.toString() ?: clipboard ?: title
    }

    fun toRankCandidate(): RankCandidate {
        val source = when (kind) {
            NativeKind.App -> UnifiedSource.APP
            NativeKind.File -> UnifiedSource.FILE
            NativeKind.Setting -> UnifiedSource.SETTING
            NativeKind.Calculator -> UnifiedSource.COMMAND
            NativeKind.Command -> UnifiedSource.COMMAND
            NativeKind.Clipboard, NativeKind.SearchHistory -> UnifiedSource.HISTORY
            NativeKind.Web -> UnifiedSource.WEB
            NativeKind.BluetoothDevice -> UnifiedSource.CONTROL
            else -> UnifiedSource.ALIAS
        }
        return RankCandidate(
            id = kind.name + ":" + rankIdentity(),
            title = title,
            subtitle = subtitle,
            source = source,
            aliases = listOfNotNull(
                subtitle.takeIf(String::isNotBlank),
                app?.packageName,
                setting?.action,
                file?.mimeType,
            ),
        )
    }
}

/** Stable de-duplication id used by ranking and list rendering. */
internal fun nativeResultId(result: NativeResult): String =
    result.kind.name + ":" + result.rankIdentity()

/** Deduplicated, order-preserving. Pure; JVM-testable. */
internal fun dedupeNativeResults(results: List<NativeResult>): List<NativeResult> =
    results.distinctBy(::nativeResultId)

/** Pure music-control query predicate extracted from the panel filter. JVM-testable. */
internal fun matchesMusicQuery(kind: NativeKind, title: String, query: String): Boolean {
    val term = query.trim().lowercase()
    return when (kind) {
        NativeKind.MusicNext -> term in listOf("next", "next track", "skip", "skip track") || title.contains(term, true) && term.length > 3
        NativeKind.MusicPrevious -> term in listOf("previous", "previous track", "back track") || title.contains(term, true) && term.length > 3
        NativeKind.MusicToggle -> term in listOf("play", "pause", "play music", "pause music", "resume") || title.contains(term, true) && term.length > 3
        NativeKind.MusicOpen -> term in listOf("open player", "player", "music app") || title.contains(term, true) && term.length > 3
        else -> false
    }
}

internal fun filterMusicByQuery(items: List<NativeResult>, query: String): List<NativeResult> =
    items.filter { matchesMusicQuery(it.kind, it.title, query) }

/**
 * Unified ranking for the native surface. Reuses the pure [UnifiedRanker] so the
 * phone feels like one search engine: typo-tolerant text tiers dominate, learned
 * selections and app usage break ties deterministically. Unavailable sources never
 * block: any provider failure yields an empty candidate list, not an exception.
 */
internal fun rankNativeResults(
    unranked: List<NativeResult>,
    query: String,
    engine: CommandEngine,
    appOrderHint: List<NativeResult> = emptyList(),
): List<NativeResult> {
    if (unranked.isEmpty()) return unranked
    return try {
        val candidates = unranked.map { it.toRankCandidate() }
        val ids = candidates.map { it.id }
        val appIds = unranked.mapNotNull { it.app?.packageName?.let { pkg -> "app:$pkg" } }
        val usage = try {
            UnifiedSearchEngine.usageWeights(engine.context, appIds)
        } catch (_: Exception) {
            emptyMap()
        }.mapKeys { (key, _) ->
            // Map "app:<pkg>" back onto this surface's "App:<pkg>" candidate ids.
            "App:" + key.removePrefix("app:")
        }
        val counts = HashMap<String, Int>()
        val times = HashMap<String, Long>()
        for (result in unranked) {
            val id = result.kind.name + ":" + result.rankIdentity()
            CommandBarRanker.score(engine.context, result.kind.name, result.rankIdentity())
                .takeIf { it > 0 }?.let { counts[id] = it }
            CommandBarRanker.lastSelectedAt(engine.context, result.kind.name, result.rankIdentity())
                .takeIf { it > 0L }?.let { times[id] = it }
        }
        // Preserve the previous app-usage order as a final deterministic nudge for
        // blank queries: it is encoded as a tiny usage weight, never as a reorder.
        if (query.isBlank() && appOrderHint.isNotEmpty()) {
            val order = appOrderHint.map { it.kind.name + ":" + it.rankIdentity() }
            order.forEachIndexed { index, id ->
                if (id !in counts && id !in usage) {
                    // No-op placeholder keeps the map shape stable; ranking falls back to title.
                }
            }
        }
        val signals = RankingSignals(counts, times, System.currentTimeMillis(), usage)
        val intent = try {
            SearchIntentParser.parse(query)
        } catch (_: Exception) {
            SearchIntent.Plain
        }
        val rankedIds = UnifiedAggregator.rank(query, candidates, signals, intent, limit = ids.size * 2 + 8)
            .map { it.candidate.id }
        val byId = unranked.associateBy { it.kind.name + ":" + it.rankIdentity() }
        // Deduplicated, stable, deterministic: unknown ids (should not happen) are appended.
        val ordered = rankedIds.mapNotNull(byId::get)
        val missing = unranked.filter { it.kind.name + ":" + it.rankIdentity() !in rankedIds.toSet() }
        (ordered + missing).distinctBy { it.kind.name + ":" + it.rankIdentity() }
    } catch (_: Exception) {
        unranked
    }
}

internal fun musicResults(player: CommandBarDeviceActions.Player?): List<NativeResult> = listOf(
    NativeResult("Previous track", "Music", NativeKind.MusicPrevious),
    NativeResult(if (player?.playing == true) "Pause music" else "Play music", player?.title ?: "Music", NativeKind.MusicToggle),
    NativeResult("Next track", "Music", NativeKind.MusicNext),
    NativeResult("Open player", player?.packageName ?: "Music app", NativeKind.MusicOpen),
)
