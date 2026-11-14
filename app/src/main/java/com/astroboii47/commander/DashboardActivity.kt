package com.astroboii47.commander

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DashboardTheme { DashboardScreen(launchIntent = intent) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setContent { DashboardTheme { DashboardScreen(launchIntent = intent) } }
    }

    override fun onResume() {
        super.onResume()
        EdgeBarSettings.ensureStarted(this)
    }
}

@Composable
private fun DashboardTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFFB9A7FF))
    else lightColorScheme(primary = Color(0xFF6650A4))
    MaterialTheme(colorScheme = colors) {
        androidx.compose.runtime.SideEffect {
            val activity = context as? android.app.Activity ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
        content()
    }
}

private enum class DashTab { HOME, CALENDAR, SCHOOL, SWIM, MEMORIES }

@Composable
private fun DashboardScreen(launchIntent: Intent? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var tab by remember { mutableStateOf(DashTab.HOME) }

    // Shared calendar navigation state (home agenda can jump into the Calendar tab).
    var calDay by remember { mutableStateOf(LocalDate.now()) }

    // Shared school navigation state (detail survives refreshes, not tab switches).
    var openCourseId by remember { mutableStateOf<String?>(null) }

    // iCal state
    val feeds = remember { mutableStateListOf<ICalFeed>() }
    var feedsVersion by remember { mutableIntStateOf(0) }
    var syncingFeeds by remember { mutableStateOf(false) }

    // PowerSchool state
    var psUi by remember { mutableStateOf<DashboardPsUi>(DashboardPsUi.SignedOut) }
    var psProgress by remember { mutableStateOf("") }
    var psUser by remember { mutableStateOf(PowerSchoolStore.username(context).orEmpty()) }
    var psPassword by remember { mutableStateOf("") }
    var psServer by remember { mutableStateOf(PowerSchoolStore.baseUrl(context)) }
    var psShowPassword by remember { mutableStateOf(false) }
    var selectedTerm by remember { mutableStateOf(PowerSchoolStore.selectedTerm(context)) }
    var psCourses by remember { mutableStateOf(listOf<PsCourse>()) }
    var psRefreshedAt by remember { mutableStateOf(PowerSchoolStore.refreshedAt(context)) }
    var gradeHist by remember { mutableStateOf(listOf<PowerSchoolStore.GradeSnapshot>()) }

    // Legacy single-goal swim state (kept: migration source + home compatibility).
    var swim by remember { mutableStateOf(SwimStore.load(context)) }
    var swimExpanded by remember { mutableStateOf(false) }

    // Swim platform state
    var swimProfile by remember { mutableStateOf(SwimmerProfile()) }
    var swimGoals by remember { mutableStateOf(listOf<SwimGoal>()) }
    var practices by remember { mutableStateOf(listOf<PracticeSession>()) }
    var raceResults by remember { mutableStateOf(listOf<RaceResult>()) }
    var swimSyncLabel by remember { mutableStateOf<String?>(null) }
    var meetStatus by remember { mutableStateOf<String?>(null) }
    var showMeetConnect by remember { mutableStateOf(false) }

    // Memories state
    var memories by remember { mutableStateOf(listOf<MemoryRecord>()) }
    var memoryTrash by remember { mutableStateOf(listOf<MemoryRecord>()) }
    var memoryCollections by remember { mutableStateOf(listOf<MemoryCollection>()) }
    var memoryQuery by remember { mutableStateOf("") }
    var openMemory by remember { mutableStateOf<MemoryRecord?>(null) }
    var viewerFile by remember { mutableStateOf<java.io.File?>(null) }
    var showReflect by remember { mutableStateOf(false) }

    // Intelligence state
    var insights by remember { mutableStateOf(listOf<Insight>()) }
    var alertPrefs by remember { mutableStateOf(SchoolAlertPrefs()) }
    var autoRules by remember { mutableStateOf(listOf<AutomationRule>()) }
    var autoRuns by remember { mutableStateOf(listOf<AutomationRun>()) }
    var notifyPrefs by remember { mutableStateOf(mapOf<String, Boolean>()) }

    fun reloadSwimPlatform() {
        swimProfile = SwimPlatformStore.loadProfile(context)
        swimGoals = SwimPlatformStore.loadGoals(context)
        practices = SwimPlatformStore.loadPractices(context)
        raceResults = SwimPlatformStore.loadResults(context)
        swimSyncLabel = SwimPlatformStore.lastResultsSync(context)
    }

    fun reloadMemories() {
        memories = MemoryStore.loadMemories(context)
        memoryTrash = MemoryStore.loadMemories(context, includeTrashed = true).filter { it.trashed }
        memoryCollections = MemoryStore.loadCollections(context)
    }

    fun reloadIntelligence() {
        insights = rankInsights(InsightStore.load(context))
        alertPrefs = SchoolAlertStore.load(context)
        autoRules = AutomationEngine.loadRules(context)
        autoRuns = AutomationEngine.loadRuns(context)
        notifyPrefs = listOf(
            HoustonNotifyPrefs.CH_BRIEFING, HoustonNotifyPrefs.CH_SCHOOL,
            HoustonNotifyPrefs.CH_SWIM, HoustonNotifyPrefs.CH_MEMORY, HoustonNotifyPrefs.CH_AUTO,
        ).associateWith { HoustonNotifyPrefs.enabled(context, it) }
    }

    fun publishInsights(newOnes: List<Insight>) {
        if (newOnes.isEmpty()) return
        val fresh = InsightStore.ingest(context, newOnes)
        reloadIntelligence()
        if (fresh.isNotEmpty()) {
            InsightNotifier.notifyNew(context, rankInsights(fresh))
        }
    }

    fun reloadFeeds() {
        feeds.clear()
        feeds.addAll(ICalStore.list(context))
        feedsVersion++
    }

    fun reloadCachedPs() {
        val cached = PowerSchoolStore.loadCourses(context)
        psCourses = cached
        gradeHist = PowerSchoolStore.gradeHistory(context)
        if (cached.isNotEmpty()) {
            val terms = psCourseTerms(cached)
            if (selectedTerm == null || selectedTerm !in terms) {
                selectedTerm = terms.maxByOrNull { t -> cached.sumOf { c -> if (c.term == t) c.assignments.size else 0 } }
                selectedTerm?.let { PowerSchoolStore.setSelectedTerm(context, it) }
            }
            psUi = DashboardPsUi.Content(error = null, syncing = false, stale = true)
        }
    }

    fun handleSharedIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(4000)
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        if (text.isNullOrBlank()) return
        val mem = MemoryStore.upsert(
            context,
            MemoryRecord(title = subject?.take(80).orEmpty(), body = text, source = "share", originInfo = "Android share sheet"),
        )
        reloadMemories()
        MemoryAnalysisRunner.enqueueExtract(context, mem.uuid, scope)
        publishInsights(
            listOf(
                Insight(
                    id = "memory.created:${mem.uuid}",
                    kind = InsightKind.MEMORY,
                    title = "Memory saved from share",
                    explanation = (subject ?: text.take(80)).take(140),
                    refs = listOf("memory:${mem.uuid}"),
                    score = 40,
                    actionLabel = "Open memory",
                    actionTarget = "memory:${mem.uuid}",
                ),
            ),
        )
        tab = DashTab.MEMORIES
        Toast.makeText(context, "Saved to Memories", Toast.LENGTH_SHORT).show()
        intent.action = null
    }

    fun syncOneFeed(feed: ICalFeed) {
        scope.launch {
            syncingFeeds = true
            try {
                val result = withContext(Dispatchers.IO) { ICalStore.refresh(context, feed) }
                result.exceptionOrNull()?.let {
                    Toast.makeText(context, "${feed.name}: ${it.message?.take(120)}", Toast.LENGTH_LONG).show()
                }
            } finally {
                reloadFeeds()
                syncingFeeds = false
            }
        }
    }

    fun syncAllFeeds() {
        scope.launch {
            syncingFeeds = true
            try {
                for (feed in feeds.toList()) {
                    val result = withContext(Dispatchers.IO) { ICalStore.refresh(context, feed) }
                    result.exceptionOrNull()?.let {
                        Toast.makeText(context, "${feed.name}: ${it.message?.take(120)}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                reloadFeeds()
                syncingFeeds = false
            }
        }
    }

    fun doPsRefresh(account: String?, password: String?) {
        scope.launch {
            psProgress = "Connecting…"
            psUi = DashboardPsUi.SigningIn("Connecting…")
            try {
                val prev = PowerSchoolStore.loadCourses(context)
                val prevKeys = prev.flatMap { it.assignments }.map(::assignmentKey).toSet()
                val client = PowerSchoolStore.client(context)
                val raw = withContext(Dispatchers.IO) {
                    client.collectAll(account, password) { done, total ->
                        psProgress = "Score pages $done/$total…"
                    }
                }
                val normalized = normalizeRawRecords(raw, LocalDate.now())
                val stamp = java.time.Instant.now().toString()
                withContext(Dispatchers.IO) {
                    PowerSchoolStore.saveCourses(context, normalized, stamp)
                    PowerSchoolStore.recordGradeSnapshot(context, normalized, LocalDate.now().toString())
                }
                if (account != null) PowerSchoolStore.setUsername(context, account)
                psCourses = normalized
                psRefreshedAt = stamp
                gradeHist = PowerSchoolStore.gradeHistory(context)
                val terms = psCourseTerms(normalized)
                if (selectedTerm == null || selectedTerm !in terms) {
                    selectedTerm = terms.maxByOrNull { t -> normalized.sumOf { c -> if (c.term == t) c.assignments.size else 0 } }
                    selectedTerm?.let { PowerSchoolStore.setSelectedTerm(context, it) }
                }
                psPassword = ""
                psUi = DashboardPsUi.Content(error = null, syncing = false, stale = false)
                // School intelligence: compare compatible snapshots only.
                val scopedPrev = if (selectedTerm == null) prev else prev.filter { it.term == selectedTerm }
                val scopedCur = if (selectedTerm == null) normalized else normalized.filter { it.term == selectedTerm }
                publishInsights(detectSchoolChanges(scopedPrev, scopedCur, alertPrefs, prevKeys))
            } catch (e: PsException) {
                val cached = PowerSchoolStore.loadCourses(context)
                psCourses = cached
                gradeHist = PowerSchoolStore.gradeHistory(context)
                psUi = if (e is PsException.SessionExpired && cached.isEmpty()) {
                    DashboardPsUi.SignedOut
                } else {
                    DashboardPsUi.Content(error = psUserMessage(e), syncing = false, stale = true)
                }
                if (e !is PsException.SessionExpired || cached.isEmpty()) {
                    Toast.makeText(context, psUserMessage(e), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                val cached = PowerSchoolStore.loadCourses(context)
                psCourses = cached
                gradeHist = PowerSchoolStore.gradeHistory(context)
                psUi = DashboardPsUi.Content(error = "Refresh failed: ${(e.message ?: "unknown error").take(160)}", syncing = false, stale = true)
            }
        }
    }

    fun navigateToTarget(target: String) {
        when {
            target.startsWith("school:") -> {
                openCourseId = target.removePrefix("school:")
                tab = DashTab.SCHOOL
            }
            target.startsWith("swim:") -> tab = DashTab.SWIM
            target.startsWith("memory:") -> {
                val uuid = target.removePrefix("memory:")
                openMemory = MemoryStore.loadMemories(context, includeTrashed = true).firstOrNull { it.uuid == uuid }
                tab = DashTab.MEMORIES
            }
            else -> tab = DashTab.HOME
        }
    }

    LaunchedEffect(Unit) {
        reloadFeeds()
        if (PowerSchoolStore.username(context) != null && PowerSchoolStore.loadCourses(context).isNotEmpty()) {
            reloadCachedPs()
        }
        reloadSwimPlatform()
        reloadMemories()
        reloadIntelligence()
        InsightStore.pruneExpired(context)
        runCatching { HoustonScheduler.reschedule(context) }
        // Evaluate automation rules on start; record runs (no repeat alerts).
        val fired = AutomationEngine.evaluate(autoRules, emptyList(), insights)
        if (fired.isNotEmpty()) {
            val now = java.time.Instant.now().toString()
            for ((rule, reason) in fired) {
                AutomationEngine.recordRun(context, AutomationRun(rule.id, now, true, "start: $reason"))
            }
            autoRuns = AutomationEngine.loadRuns(context)
        }
        handleSharedIntent(launchIntent)
        launchIntent?.getStringExtra("insight_target")?.let { navigateToTarget(it) }
    }

    // Terms + GPA + movers for the selected term.
    val terms = remember(psCourses) { psCourseTerms(psCourses) }
    val termCourses = remember(psCourses, selectedTerm) {
        val t = selectedTerm
        (if (t == null) psCourses else psCourses.filter { it.term == t }).sortedBy { it.name.lowercase() }
    }
    val gpa = remember(termCourses) { psCourseGpa(termCourses) }
    val gpaDelta = remember(gradeHist) {
        val gs = gradeHist.mapNotNull { it.gpa }
        if (gs.size >= 2) gs.last() - gs[gs.size - 2] else null
    }
    val letterCounts = remember(termCourses) { psLetterCounts(termCourses) }
    val movers = remember(psCourses, gradeHist, selectedTerm) {
        val prev = gradeHist.dropLast(1).lastOrNull()?.percents ?: emptyMap()
        val cur = if (selectedTerm == null) psCourses else psCourses.filter { it.term == selectedTerm }
        psGradeMovers(prev, cur)
    }
    fun moverDate(m: PsGradeMove): String? {
        val key = "${m.courseId}|${if (selectedTerm == null) termCourses.firstOrNull { it.id == m.courseId }?.term.orEmpty() else selectedTerm}"
        var seen: Double? = null
        var firstWithNew: String? = null
        for (s in gradeHist) {
            val v = s.percents[key]
            if (v != null && v != seen) {
                if (seen != null && v == m.newPercent) firstWithNew = s.date
                seen = v
            }
        }
        return firstWithNew
    }

    // Daily briefing inputs — every line comes from connected/local data.
    val briefing = remember(feedsVersion, psCourses, swimGoals, practices, raceResults, memories, calDay) {
        val calLines = runCatching {
            ICalStore.occurrences(context, calDay, calDay).take(4).map {
                "${it.summary} ${it.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")) ?: ""}".trim()
            }
        }.getOrDefault(emptyList()) +
            PowerSchoolStore.dueOn(psCourses, calDay).take(3).map { "${it.name ?: "Assignment"} (${it.courseName})" }
        val schoolLines = buildList {
            movers.take(2).forEach { add("${it.courseName}: ${"%.1f".format(it.oldPercent)}% → ${"%.1f".format(it.newPercent)}%") }
            PowerSchoolStore.upcoming(psCourses, LocalDate.now(), 3).take(3).forEach {
                add("${it.name ?: "Assignment"} due ${it.dueIso ?: it.dueMdy ?: "soon"} (${it.courseName})")
            }
            if (psCourses.isNotEmpty() && isEmpty()) add("${termCourses.size} classes tracked · GPA ${gpa?.let { "%.2f".format(it) } ?: "—"}")
        }
        val swimLines = buildList {
            swimGoals.filter { !it.completed }.take(2).forEach {
                add("Next goal: ${it.label} → ${formatSwimTime(it.targetSec)} (best ${it.currentBestSec?.let { b -> formatSwimTime(b) } ?: "—"})")
            }
            raceResults.take(1).forEach { add("Latest verified: ${it.eventName} ${it.timeSec?.let { t -> formatSwimTime(t) } ?: ""} · ${it.meetName}".trim()) }
        }
        val memLines = memories.take(2).map { (it.title.ifBlank { it.body.take(50) }).take(80) }
        buildDailyBriefing(
            today = calDay,
            calendarLines = calLines.take(5),
            schoolLines = schoolLines.take(4),
            swimLines = swimLines.take(3),
            memoryLines = memLines,
        )
    }
    val lastSyncLabel = remember(psRefreshedAt, swimSyncLabel) {
        listOfNotNull(
            psRefreshedAt?.let { "School ${it.take(16).replace('T', ' ')}" },
            swimSyncLabel?.let { "Swim ${it.take(16).replace('T', ' ')}" },
        ).joinToString(" · ").ifEmpty { null }
    }

    // Weekly review inputs — missing data reported honestly, never padded.
    val weekly = remember(psCourses, practices, raceResults, memories, movers, feedsVersion) {
        val weekAgoDay = LocalDate.now().minusDays(7)
        val weekAgo = weekAgoDay.toString()
        val calCount = runCatching { ICalStore.occurrences(context, weekAgoDay, LocalDate.now()).size }.getOrDefault(0)
        val recentPractices = practices.filter { it.dateIso >= weekAgo }
        val recentResults = raceResults.filter { (it.dateIso ?: "") >= weekAgo }
        val recentMemories = memories.filter { it.createdIso >= weekAgo }
        val recentAssign = PowerSchoolStore.distinctAssignments(psCourses)
            .filter { a -> a.dueIso?.let { psIsoToLocalDate(it)?.let { d -> !d.isBefore(weekAgoDay) } } == true }
        buildWeeklyReview(
            weekLabel = "${weekAgoDay.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))} – " +
                LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d")),
            calendarHighlights = if (calCount == 0) emptyList() else listOf("$calCount events on your calendars"),
            assignmentActivity = recentAssign.take(4).map {
                "${it.name ?: "Assignment"} (${it.courseName}) · ${it.status}"
            },
            gradeChanges = movers.map { "${it.courseName}: ${"%.1f".format(it.oldPercent)}% → ${"%.1f".format(it.newPercent)}%" },
            swimResults = recentResults.take(3).map {
                "${it.eventName} ${it.timeSec?.let { t -> formatSwimTime(t) } ?: ""} · ${it.meetName}".trim()
            },
            practiceVolume = if (recentPractices.isEmpty()) emptyList() else listOf(
                "${recentPractices.size} practices · ${recentPractices.sumOf { it.distanceYd ?: 0 }} yd",
            ),
            memoryHighlights = recentMemories.take(3).map { (it.title.ifBlank { it.body.take(50) }).take(80) },
        )
    }

    val homeScroll = rememberScrollState()
    val calScroll = rememberScrollState()
    val schoolScroll = rememberScrollState()
    val swimScroll = rememberScrollState()
    val memScroll = rememberScrollState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(180))
                    },
                    label = "dash-tab",
                ) { page ->
                when (page) {
                    DashTab.HOME -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(homeScroll),
                        ) {
                            // Home-only header: full-bleed waves behind the status
                            // bar, scrolling away with the content so it never
                            // covers anything.
                            Box(Modifier.fillMaxWidth()) {
                                HeaderWaves(Modifier.fillMaxWidth().height(190.dp))
                                Row(
                                    Modifier.fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 18.dp)
                                        .padding(top = 10.dp, bottom = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    WaveformMark()
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Houston", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                                        Icon(Icons.Outlined.Settings, "Houston Settings")
                                    }
                                }
                            }
                            Column(Modifier.padding(horizontal = 18.dp)) {
                            HomePage(
                                feeds = feeds,
                                feedsVersion = feedsVersion,
                                psCourses = psCourses,
                                selectedTerm = selectedTerm,
                                gpa = gpa,
                                gpaDelta = gpaDelta,
                                letterCounts = letterCounts,
                                movers = movers,
                                moverDate = ::moverDate,
                                swim = swim,
                                swimExpanded = swimExpanded,
                                onToggleSwimExpanded = { swimExpanded = !swimExpanded },
                                onSaveSwimTargets = { name, s, b, g ->
                                    SwimStore.saveTargets(context, name, s, b, g)
                                    swim = SwimStore.load(context)
                                },
                                onAddSwimEntry = { date, title, detail ->
                                    SwimStore.addEntry(context, date, title, detail)
                                    swim = SwimStore.load(context)
                                },
                                onDeleteSwimEntry = {
                                    SwimStore.removeEntry(context, it)
                                    swim = SwimStore.load(context)
                                },
                                onOpenCalendar = { day ->
                                    calDay = day
                                    tab = DashTab.CALENDAR
                                },
                                onOpenSchool = { tab = DashTab.SCHOOL },
                                briefing = briefing,
                                insights = insights.filter { !it.acked }.take(5),
                                weekly = weekly,
                                lastSyncLabel = lastSyncLabel,
                                onRefreshAll = {
                                    syncAllFeeds()
                                    doPsRefresh(null, null)
                                },
                                onAckInsight = { id ->
                                    InsightStore.ack(context, id)
                                    reloadIntelligence()
                                },
                                onOpenInsight = { navigateToTarget(it.actionTarget ?: it.id) },
                                onReflect = { showReflect = true },
                            )
                            if (showReflect) {
                                EveningReflectionDialog(
                                    onSave = { text, voice ->
                                        val mem = MemoryStore.upsert(
                                            context,
                                            MemoryRecord(
                                                title = "Reflection · ${LocalDate.now()}",
                                                body = text,
                                                voiceUris = listOfNotNull(voice),
                                                source = "reflection",
                                            ),
                                        )
                                        reloadMemories()
                                        MemoryAnalysisRunner.enqueueExtract(context, mem.uuid, scope)
                                        showReflect = false
                                        Toast.makeText(context, "Reflection saved", Toast.LENGTH_SHORT).show()
                                    },
                                    onSkip = { showReflect = false },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    DashTab.CALENDAR -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(calScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 6.dp),
                        ) {
                            CalendarPage(
                                feeds = feeds,
                                feedsVersion = feedsVersion,
                                psCourses = psCourses,
                                initialDay = calDay,
                                onFeedsChanged = { reloadFeeds() },
                                onSyncFeed = { syncOneFeed(it) },
                                onSyncFeeds = { syncAllFeeds() },
                                syncingFeeds = syncingFeeds,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    DashTab.SCHOOL -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(schoolScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 6.dp),
                        ) {
                            // Map the dashboard auth state onto the page's view state.
                            val pageUi: DashboardPsUi = when (val s = psUi) {
                                is DashboardPsUi.SignedOut -> DashboardPsUi.SignedOut
                                is DashboardPsUi.SigningIn -> DashboardPsUi.SigningIn(s.note)
                                is DashboardPsUi.Content -> s
                            }
                            SchoolPage(
                                psUi = pageUi,
                                progress = psProgress,
                                courses = termCourses,
                                allCourses = psCourses,
                                refreshedAt = psRefreshedAt,
                                username = psUser,
                                password = psPassword,
                                server = psServer,
                                showPassword = psShowPassword,
                                selectedTerm = selectedTerm,
                                terms = terms,
                                gpa = gpa,
                                gpaDelta = gpaDelta,
                                letterCounts = letterCounts,
                                movers = movers,
                                moverDate = ::moverDate,
                                openCourseId = openCourseId,
                                onOpenCourse = { openCourseId = it },
                                onTerm = {
                                    selectedTerm = it
                                    PowerSchoolStore.setSelectedTerm(context, it)
                                    openCourseId = null
                                },
                                onUsername = { psUser = it },
                                onPassword = { psPassword = it },
                                onServer = { psServer = it },
                                onTogglePassword = { psShowPassword = !psShowPassword },
                                onSignIn = {
                                    PowerSchoolStore.setBaseUrl(context, psServer.ifBlank { PS_DEFAULT_BASE_URL })
                                    doPsRefresh(psUser.trim().ifEmpty { null }, psPassword.ifEmpty { null })
                                },
                                onRefresh = {
                                    psUi = DashboardPsUi.Content(error = null, syncing = true, stale = true)
                                    doPsRefresh(null, null)
                                },
                                onSignOut = {
                                    PowerSchoolStore.clearAccount(context)
                                    psCourses = emptyList()
                                    psPassword = ""
                                    psUser = ""
                                    selectedTerm = null
                                    gradeHist = emptyList()
                                    openCourseId = null
                                    psUi = DashboardPsUi.SignedOut
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                            SchoolAlertsCard(prefs = alertPrefs, onSave = {
                                SchoolAlertStore.save(context, it)
                                alertPrefs = it
                            })
                            Spacer(Modifier.height(12.dp))
                            AutomationsCard(
                                rules = autoRules,
                                runs = autoRuns,
                                notifyPrefs = notifyPrefs,
                                onToggleRule = { id, on ->
                                    val updated = autoRules.map { if (it.id == id) it.copy(enabled = on) else it }
                                    AutomationEngine.saveRules(context, updated)
                                    autoRules = updated
                                    AutomationEngine.recordRun(
                                        context,
                                        AutomationRun(id, java.time.Instant.now().toString(), true, if (on) "Enabled" else "Disabled"),
                                    )
                                    autoRuns = AutomationEngine.loadRuns(context)
                                },
                                onToggleChannel = { ch, on ->
                                    HoustonNotifyPrefs.setEnabled(context, ch, on)
                                    reloadIntelligence()
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    DashTab.SWIM -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(swimScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 6.dp),
                        ) {
                            SwimPlatformPage(
                                profile = swimProfile,
                                goals = swimGoals,
                                practices = practices,
                                results = raceResults,
                                lastSync = swimSyncLabel,
                                meetStatus = meetStatus,
                                onSaveProfile = {
                                    SwimPlatformStore.saveProfile(context, it)
                                    reloadSwimPlatform()
                                },
                                onSaveGoal = { g ->
                                    SwimPlatformStore.saveGoals(context, swimGoals.filter { it.id != g.id } + g)
                                    reloadSwimPlatform()
                                },
                                onDeleteGoal = { id ->
                                    SwimPlatformStore.saveGoals(context, swimGoals.filter { it.id != id })
                                    reloadSwimPlatform()
                                },
                                onAddPractice = { p ->
                                    SwimPlatformStore.savePractices(context, listOf(p) + practices)
                                    reloadSwimPlatform()
                                    publishInsights(
                                        listOf(
                                            Insight(
                                                id = "swim.practice:${p.id}",
                                                kind = InsightKind.SWIM,
                                                title = "Practice logged (${p.dateIso})",
                                                explanation = listOfNotNull(
                                                    p.distanceYd?.let { "$it yd" },
                                                    p.durationMin?.let { "$it min" },
                                                    p.notes.takeIf { it.isNotBlank() },
                                                ).joinToString(" · ").ifEmpty { "Practice recorded." },
                                                refs = listOf("practice:${p.id}"),
                                                score = 35,
                                                actionLabel = "Open swim",
                                                actionTarget = "swim:goals",
                                            ),
                                        ),
                                    )
                                },
                                onDeletePractice = { id ->
                                    SwimPlatformStore.savePractices(context, practices.filter { it.id != id })
                                    reloadSwimPlatform()
                                },
                                onAddResult = { r ->
                                    val fresh = SwimPlatformStore.importResults(context, listOf(r))
                                    if (fresh.isNotEmpty()) {
                                        val (updated, goalInsights) = applyResultToGoals(swimGoals, fresh.first())
                                        SwimPlatformStore.saveGoals(context, updated)
                                        reloadSwimPlatform()
                                        publishInsights(goalInsights)
                                    } else {
                                        meetStatus = "That result is already imported — no duplicates added."
                                    }
                                },
                                onConnectResults = { showMeetConnect = true },
                            )
                            if (showMeetConnect) {
                                MeetImportDialog(
                                    profileName = swimProfile.name,
                                    onDismiss = { showMeetConnect = false },
                                    onImport = { imported, label ->
                                        val fresh = SwimPlatformStore.importResults(context, imported)
                                        var goalInsights: List<Insight> = emptyList()
                                        if (fresh.isNotEmpty()) {
                                            var cur = swimGoals
                                            val all = mutableListOf<Insight>()
                                            for (res in fresh) {
                                                val (u, ins) = applyResultToGoals(cur, res)
                                                cur = u
                                                all.addAll(ins)
                                            }
                                            SwimPlatformStore.saveGoals(context, cur)
                                            goalInsights = all
                                        }
                                        reloadSwimPlatform()
                                        meetStatus = if (fresh.isEmpty()) {
                                            "No new results in $label — duplicates skipped, nothing invented."
                                        } else {
                                            "Imported ${fresh.size} verified result${if (fresh.size == 1) "" else "s"} from $label."
                                        }
                                        publishInsights(
                                            goalInsights + listOf(
                                                Insight(
                                                    id = "swim.result_imported:${fresh.firstOrNull()?.id ?: label}",
                                                    kind = InsightKind.SWIM,
                                                    title = "Results imported from $label",
                                                    explanation = "${fresh.size} new verified result${if (fresh.size == 1) "" else "s"}.",
                                                    refs = fresh.map { "race:${it.id}" },
                                                    score = 55,
                                                    actionLabel = "Open swim",
                                                    actionTarget = "swim:goals",
                                                ),
                                            ),
                                        )
                                        showMeetConnect = false
                                    },
                                    onStatus = { meetStatus = it },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    DashTab.MEMORIES -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(memScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 6.dp),
                        ) {
                            if (openMemory != null) {
                                MemoryDetailSheet(
                                    memory = openMemory!!,
                                    collections = memoryCollections,
                                    onSave = {
                                        MemoryStore.upsert(context, it)
                                        reloadMemories()
                                        openMemory = it
                                    },
                                    onClose = { openMemory = null },
                                    onRetryAnalysis = { uuid ->
                                        MemoryAnalysisRunner.enqueueExtract(context, uuid, scope)
                                        Toast.makeText(context, "Analysis re-queued", Toast.LENGTH_SHORT).show()
                                    },
                                    onViewImage = { viewerFile = it },
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            val shown = remember(memoryQuery, memories) {
                                if (memoryQuery.isBlank()) memories
                                else MemoryStore.search(context, memoryQuery)
                            }
                            MemoriesPage(
                                memories = shown,
                                collections = memoryCollections,
                                trash = memoryTrash,
                                query = memoryQuery,
                                onQuery = { memoryQuery = it },
                                onSave = { m ->
                                    val saved = MemoryStore.upsert(context, m)
                                    reloadMemories()
                                    MemoryAnalysisRunner.enqueueExtract(context, saved.uuid, scope)
                                    publishInsights(
                                        listOf(
                                            Insight(
                                                id = "memory.created:${saved.uuid}",
                                                kind = InsightKind.MEMORY,
                                                title = "Memory captured",
                                                explanation = (saved.title.ifBlank { saved.body.take(80) }).take(140).ifEmpty { "New memory saved." },
                                                refs = listOf("memory:${saved.uuid}"),
                                                score = 40,
                                                actionLabel = "Open memory",
                                                actionTarget = "memory:${saved.uuid}",
                                            ),
                                        ),
                                    )
                                },
                                onTrash = {
                                    MemoryStore.trash(context, it)
                                    reloadMemories()
                                },
                                onRestore = {
                                    MemoryStore.restore(context, it)
                                    reloadMemories()
                                },
                                onDelete = {
                                    MemoryStore.deletePermanently(context, it)
                                    reloadMemories()
                                },
                                onToggleFavorite = { uuid ->
                                    memories.firstOrNull { it.uuid == uuid }?.let {
                                        MemoryStore.upsert(context, it.copy(favorite = !it.favorite))
                                        reloadMemories()
                                    }
                                },
                                onOpen = { openMemory = it },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                }
                // Full-screen image viewer overlay (above tab content, below nav bar).
                if (viewerFile != null) {
                    Box(Modifier.matchParentSize()) {
                        ImageViewerDialog(file = viewerFile!!, onClose = { viewerFile = null })
                    }
                }
            }

            // Floating M3E-style bar: pill with margins, never covering content.
            Box(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                NavigationBarItem(
                    selected = tab == DashTab.HOME,
                    onClick = { tab = DashTab.HOME },
                    icon = { Icon(Icons.Outlined.Home, "Home") },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == DashTab.CALENDAR,
                    onClick = { tab = DashTab.CALENDAR },
                    icon = { Icon(Icons.Outlined.CalendarMonth, "Calendar") },
                    label = { Text("Calendar") },
                )
                NavigationBarItem(
                    selected = tab == DashTab.SCHOOL,
                    onClick = { tab = DashTab.SCHOOL },
                    icon = { Icon(Icons.Outlined.School, "School") },
                    label = { Text("School") },
                )
                NavigationBarItem(
                    selected = tab == DashTab.SWIM,
                    onClick = { tab = DashTab.SWIM },
                    icon = { Icon(Icons.Outlined.Waves, "Swim") },
                    label = { Text("Swim") },
                )
                NavigationBarItem(
                    selected = tab == DashTab.MEMORIES,
                    onClick = { tab = DashTab.MEMORIES },
                    icon = { Icon(Icons.Outlined.Book, "Memories") },
                    label = { Text("Memories") },
                )
                    }
                }
            }
        }
    }
}
