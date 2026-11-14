package com.astroboii47.commander

/**
 * Combines per-provider candidates into one deterministically ranked list.
 *
 * Providers are NOT rewritten: callers keep using [AppCatalog], [SystemSettingsCatalog],
 * [ControlActions], [CommandEngine.searchContacts]/[searchFiles] etc, convert each hit
 * to a [RankCandidate], then call [rank]. Slow sources (files, contacts) can arrive
 * later via [mergeAsync]: previously visible ids keep their relative order unless a
 * newcomer scores strictly higher, which avoids distracting reordering while the
 * user navigates with Up/Down or 1-0.
 */
object UnifiedAggregator {
    /**
     * Rank [candidates] for [query], preserving explicit triggers as hard filters.
     *
     * When [intent] is a [SearchIntent.Trigger], callers must route to the scoped
     * provider instead of calling this (fast, reliable filter path). This function
     * additionally drops candidates whose source is excluded by [allowedSources].
     */
    fun rank(
        query: String,
        candidates: List<RankCandidate>,
        signals: RankingSignals = RankingSignals(),
        intent: SearchIntent = SearchIntent.Plain,
        allowedSources: Set<UnifiedSource>? = null,
        limit: Int = 20,
    ): List<ScoredCandidate> {
        var list = candidates
        if (allowedSources != null) list = list.filter { it.source in allowedSources }
        list = applyIntentBoost(list, intent)
        val ranked = UnifiedRanker.rank(query, list, signals, limit * 2)
        return deduplicate(ranked).take(limit.coerceAtLeast(0))
    }

    /**
     * Merge a late-arriving batch (files, contacts) into [current] without
     * reshuffling stable rows: re-rank the union, but keep any previously visible
     * row ahead of a newcomer on exact total ties (deterministic tie-breaks in
     * [UnifiedRanker] already guarantee this; this documents the contract).
     */
    fun mergeAsync(
        query: String,
        current: List<ScoredCandidate>,
        late: List<RankCandidate>,
        signals: RankingSignals = RankingSignals(),
        limit: Int = 20,
    ): List<ScoredCandidate> {
        if (late.isEmpty()) return current.take(limit)
        val union = (current.map { it.candidate } + late).distinctBy { it.id }
        return deduplicate(UnifiedRanker.rank(query, union, signals, limit * 2)).take(limit)
    }

    fun deduplicate(ranked: List<ScoredCandidate>): List<ScoredCandidate> {
        val seen = HashSet<String>()
        return ranked.filter { seen.add(it.candidate.id) }
    }

    private fun applyIntentBoost(
        candidates: List<RankCandidate>,
        intent: SearchIntent,
    ): List<RankCandidate> {
        // Intent detection never hides results; it only reorders by promoting the
        // matching source to the front of its text tier via a small source swap.
        // The tier gap in UnifiedRanker keeps strong text matches ahead regardless.
        return when (intent) {
            is SearchIntent.SystemHint -> candidates.map {
                if (it.source == UnifiedSource.SETTING || it.source == UnifiedSource.CONTROL) {
                    it.copy(source = UnifiedSource.COMMAND)
                } else it
            }
            is SearchIntent.PersonHint -> candidates.map {
                if (it.source == UnifiedSource.CONTACT || it.source == UnifiedSource.CONVERSATION) {
                    it.copy(source = UnifiedSource.COMMAND)
                } else it
            }
            is SearchIntent.Downloads -> candidates.map {
                if (it.source == UnifiedSource.FOLDER || it.source == UnifiedSource.FILE) {
                    it.copy(source = UnifiedSource.COMMAND)
                } else it
            }
            else -> candidates
        }
    }

    // --- Lightweight candidate builders reusing existing provider data shapes. ---

    fun appCandidate(label: String, packageName: String, customTerms: List<String> = emptyList()): RankCandidate =
        RankCandidate(
            id = "app:$packageName",
            title = label,
            subtitle = packageName,
            source = UnifiedSource.APP,
            aliases = listOf(packageName) + customTerms,
        )

    fun settingCandidate(label: String, subtitle: String, action: String, keywords: List<String>): RankCandidate =
        RankCandidate(
            id = "setting:$action",
            title = label,
            subtitle = subtitle,
            source = UnifiedSource.SETTING,
            aliases = keywords + listOf(subtitle),
        )

    fun controlCandidate(label: String, subtitle: String, kindName: String): RankCandidate =
        RankCandidate(
            id = "control:$kindName",
            title = label.substringBefore(" ·"),
            subtitle = subtitle,
            source = UnifiedSource.CONTROL,
            aliases = listOf(label, kindName),
        )

    fun contactCandidate(name: String, number: String, platform: String? = null): RankCandidate =
        RankCandidate(
            id = "contact:${name.lowercase()}:${number.filter(Char::isDigit)}:${platform.orEmpty().lowercase()}",
            title = name,
            subtitle = listOfNotNull(platform, number.takeIf(String::isNotBlank)).joinToString(" · "),
            source = if (platform == null) UnifiedSource.CONTACT else UnifiedSource.CONVERSATION,
            aliases = listOfNotNull(number, platform),
        )

    fun fileCandidate(name: String, uri: String, location: String, isDirectory: Boolean): RankCandidate =
        RankCandidate(
            id = "file:$uri",
            title = name,
            subtitle = location,
            source = if (isDirectory) UnifiedSource.FOLDER else UnifiedSource.FILE,
            aliases = listOf(location),
        )

    fun calculatorCandidate(expression: String, preview: String): RankCandidate = RankCandidate(
        id = "command:calc:$expression",
        title = preview,
        subtitle = "calculate · $expression",
        source = UnifiedSource.COMMAND,
        aliases = listOf(expression, "calculator", "calculate"),
    )

    fun timerCandidate(seconds: Long, label: String): RankCandidate = RankCandidate(
        id = "command:timer:$seconds:$label",
        title = "Timer ${formatDuration(seconds)}",
        subtitle = label,
        source = UnifiedSource.COMMAND,
        aliases = listOf("timer", "alarm", label),
    )

    fun webCandidate(query: String): RankCandidate = RankCandidate(
        id = "web:$query",
        title = "Search web for “$query”",
        subtitle = "web search",
        source = UnifiedSource.WEB,
        aliases = listOf("web", "search"),
    )

    fun aliasCandidate(alias: String, label: String, targetQuery: String): RankCandidate = RankCandidate(
        id = "alias:$alias:$targetQuery",
        title = "$label: $targetQuery",
        subtitle = "$alias · search alias",
        source = UnifiedSource.ALIAS,
        aliases = listOf(alias, label),
    )

    private fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
