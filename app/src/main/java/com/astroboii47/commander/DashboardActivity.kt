package com.astroboii47.commander

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.astroboii47.commander.agent.AgentEngine
import com.astroboii47.commander.agent.AgentInput
import com.astroboii47.commander.secondbrain.context.BrainContext
import com.astroboii47.commander.secondbrain.context.BrainSnapshot
import com.astroboii47.commander.secondbrain.db.AgentRun
import com.astroboii47.commander.secondbrain.di.BrainGraph
import com.astroboii47.commander.secondbrain.di.BrainPrefs
import com.astroboii47.commander.secondbrain.ui.AskScreen
import com.astroboii47.commander.secondbrain.ui.BrainHome
import com.astroboii47.commander.secondbrain.ui.MemoryScreen
import com.astroboii47.commander.secondbrain.ui.PlanScreen
import com.astroboii47.commander.secondbrain.ui.SbBottomBar
import com.astroboii47.commander.secondbrain.ui.SbDestination
import com.astroboii47.commander.secondbrain.work.BrainWorker
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

internal enum class DashTab { HOME, CALENDAR, SCHOOL, SWIM, MEMORIES }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(launchIntent: Intent? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "home"
    fun nav(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

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
    var showSwimmerLink by remember { mutableStateOf(false) }
    var pendingMeet by remember { mutableStateOf<MmMeet?>(null) }

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
    var syncOn by remember { mutableStateOf(true) }
    var syncHours by remember { mutableLongStateOf(6L) }
    var lastBgSync by remember { mutableStateOf<String?>(null) }
    var quietStart by remember { mutableIntStateOf(1380) }
    var quietEnd by remember { mutableIntStateOf(420) }
    var agentNotifyLevel by remember { mutableStateOf("important") }
    var proactiveOn by remember { mutableStateOf(true) }
    var agentRuns by remember { mutableStateOf(listOf<AgentRun>()) }

    fun reloadBrainPrefs() {
        scope.launch {
            val (s, e) = BrainPrefs.quietWindow(context)
            quietStart = s
            quietEnd = e
            agentNotifyLevel = BrainPrefs.notifyLevel(context)
            proactiveOn = BrainPrefs.proactive(context)
            agentRuns = withContext(Dispatchers.IO) { BrainGraph.db(context).runs().recent(10) }
        }
    }

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
        syncOn = HoustonSyncPrefs.syncEnabled(context)
        syncHours = HoustonSyncPrefs.intervalHours(context)
        lastBgSync = HoustonSyncPrefs.lastSync(context)
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
        nav("captures")
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

    // Second-brain state (new IA reads the same stores).
    var brainSnapshot by remember { mutableStateOf<BrainSnapshot?>(null) }
    var modelBriefing by remember { mutableStateOf<String?>(null) }
    var briefingBusy by remember { mutableStateOf(false) }
    var askPrefill by remember { mutableStateOf("") }
    var askNonce by remember { mutableIntStateOf(0) }
    val agent = remember { BrainGraph.agent(context) }

    fun refreshBrain(query: String? = null) {
        scope.launch {
            brainSnapshot = withContext(Dispatchers.IO) { BrainContext.snapshot(context, query) }
        }
    }

    fun deterministicBrief(): String? {
        val s = brainSnapshot ?: return null
        return agent.deterministicAnswer("brief me on today", s)
    }

    fun refreshModelBriefing() {
        val s = brainSnapshot ?: return
        scope.launch {
            briefingBusy = true
            try {
                val out = withContext(Dispatchers.IO) {
                    agent.run(
                        context,
                        AgentInput(text = "Give me a concise morning-style briefing for today.", trigger = "briefing"),
                        s,
                    )
                }
                modelBriefing = out.answer
            } finally {
                briefingBusy = false
            }
        }
    }

    fun navigateToTarget(target: String) {
        val (dest, payload) = SbDestination.parseTarget(target)
        when (dest) {
            SbDestination.SCHOOL -> {
                if (!payload.isNullOrBlank()) openCourseId = payload
                nav("school")
            }
            SbDestination.SWIM -> nav("swim")
            SbDestination.CAPTURES -> {
                if (!payload.isNullOrBlank()) {
                    openMemory = MemoryStore.loadMemories(context, includeTrashed = true).firstOrNull { it.uuid == payload }
                }
                nav("captures")
            }
            SbDestination.CALENDAR -> {
                payload?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> calDay = d } }
                nav("calendar")
            }
            else -> nav(SbDestination.routeOf(dest))
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
        refreshBrain()
        reloadBrainPrefs()
        InsightStore.pruneExpired(context)
        runCatching { HoustonScheduler.reschedule(context) }
        runCatching { HoustonScheduler.rescheduleSync(context) }
        scope.launch {
            // The proactive worker only runs with explicit opt-in; the toggle
            // lives next to the other background controls in settings.
            if (runCatching { BrainPrefs.proactive(context) }.getOrDefault(true)) {
                runCatching { BrainWorker.schedule(context) }
            } else {
                runCatching { BrainWorker.cancel(context) }
            }
        }
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

    // Keep the grounded snapshot and agent history fresh across navigation.
    LaunchedEffect(currentRoute) {
        refreshBrain()
        if (currentRoute == "school") reloadBrainPrefs()
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
    val refreshing = syncingFeeds || (psUi as? DashboardPsUi.Content)?.syncing == true

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
    val planScroll = rememberScrollState()
    val calScroll = rememberScrollState()
    val schoolScroll = rememberScrollState()
    val swimScroll = rememberScrollState()
    val memScroll = rememberScrollState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
            NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable("home") {
                        PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = {
                                syncAllFeeds()
                                doPsRefresh(null, null)
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(homeScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().statusBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WaveformMark()
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Houston",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                                    Icon(Icons.Outlined.Settings, "Houston Settings")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val brief = modelBriefing ?: deterministicBrief()
                            BrainHome(
                                snapshot = brainSnapshot,
                                briefing = brief,
                                briefingLoading = briefingBusy && modelBriefing == null && brief == null,
                                onRegenerateBriefing = { refreshModelBriefing() },
                                onOpenRadar = { navigateToTarget(it.ifBlank { "home" }) },
                                onOpenPlan = { nav("plan") },
                                onAsk = { t ->
                                    askPrefill = t
                                    askNonce++
                                    nav("ask")
                                },
                            )
                            Spacer(Modifier.height(12.dp))
                            SectionCard(
                                icon = Icons.Outlined.Nightlight,
                                title = "Evening reflection",
                                viewAllLabel = "Reflect",
                                onViewAll = { showReflect = true },
                            ) {
                                Text(
                                    "A line or two about today — saved as a memory, never a nag.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (weekly.lines.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                WeeklyReviewCard(week = weekly)
                            }
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
                    composable("plan") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(planScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
                        ) {
                            PlanScreen(
                                snapshot = brainSnapshot,
                                onOpenCalendar = { nav("calendar") },
                            )
                            Spacer(Modifier.height(12.dp))
                            SectionCard(icon = Icons.Outlined.Apps, title = "Domains") {
                                Text(
                                    "School, swimming, and calendar detail live here — same screens, one tap away.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { nav("school") }, modifier = Modifier.weight(1f)) { Text("School") }
                                    OutlinedButton(onClick = { nav("swim") }, modifier = Modifier.weight(1f)) { Text("Swim") }
                                    OutlinedButton(onClick = { nav("calendar") }, modifier = Modifier.weight(1f)) { Text("Calendar") }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    composable("memory") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(memScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
                        ) {
                            MemoryScreen(onOpenCaptures = { nav("captures") })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    composable("ask") {
                        Column(
                            Modifier.fillMaxSize()
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
                        ) {
                    AskScreen(
                        prefill = askPrefill,
                        prefillNonce = askNonce,
                        onPrefillConsumed = { askPrefill = "" },
                    )
                        }
                    }
                    composable("calendar") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(calScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
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
                    composable("school") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(schoolScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
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
                            val morningRule = autoRules.firstOrNull { it.id == "auto-morning-briefing" }
                            BackgroundCard(
                                briefingOn = morningRule?.enabled != false,
                                briefingTime = morningRule?.timeIso ?: "07:30",
                                syncOn = syncOn,
                                syncHours = syncHours,
                                lastSync = lastBgSync,
                                quietStart = quietStart,
                                quietEnd = quietEnd,
                                notifyLevel = agentNotifyLevel,
                                proactiveOn = proactiveOn,
                                onToggleProactive = { on ->
                                    scope.launch {
                                        BrainPrefs.setProactive(context, on)
                                        proactiveOn = on
                                        if (on) BrainWorker.schedule(context) else BrainWorker.cancel(context)
                                    }
                                },
                                onToggleBriefing = { on ->
                                    val updated = autoRules.map { if (it.id == "auto-morning-briefing") it.copy(enabled = on) else it }
                                    AutomationEngine.saveRules(context, updated)
                                    autoRules = updated
                                    if (on) HoustonScheduler.reschedule(context) else HoustonScheduler.cancel(context)
                                },
                                onToggleSync = { on ->
                                    HoustonSyncPrefs.setSyncEnabled(context, on)
                                    syncOn = on
                                    HoustonScheduler.rescheduleSync(context)
                                },
                                onInterval = { h ->
                                    HoustonSyncPrefs.setIntervalHours(context, h)
                                    syncHours = h
                                    HoustonScheduler.rescheduleSync(context)
                                },
                                onQuietPreset = { s, e ->
                                    scope.launch {
                                        BrainPrefs.setQuietWindow(context, s, e)
                                        quietStart = s
                                        quietEnd = e
                                    }
                                },
                                onNotifyLevel = { level ->
                                    scope.launch {
                                        BrainPrefs.setNotifyLevel(context, level)
                                        agentNotifyLevel = level
                                    }
                                },
                                onSyncNow = {
                                    HoustonSyncService.start(context)
                                    Toast.makeText(context, "Background sync started", Toast.LENGTH_SHORT).show()
                                },
                            )
                            AutomationsCard(
                                rules = autoRules,
                                runs = autoRuns,
                                agentRuns = agentRuns,
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
                    composable("swim") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(swimScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
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
                            onFindSwimmer = { showSwimmerLink = true },
                            linkedCount = swimProfile.links.size,
                            )
                            if (showMeetConnect) {
                                MeetImportDialog(
                                    profileName = swimProfile.name,
                                    identityLabels = (listOf(swimProfile.name) + swimProfile.links.map { it.label } + swimProfile.links.map { it.uniqueId })
                                        .map { it.trim() }.filter { it.length >= 2 },
                                    initialMeet = pendingMeet,
                                    onDismiss = { showMeetConnect = false; pendingMeet = null },
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
                                        pendingMeet = null
                                    },
                                    onStatus = { meetStatus = it },
                                )
                            }
                            if (showSwimmerLink) {
                                SwimmerLinkDialog(
                                    profile = swimProfile,
                                    onSaveLinks = { links ->
                                        SwimPlatformStore.saveProfile(context, swimProfile.copy(links = links))
                                        reloadSwimPlatform()
                                    },
                                    onImportMeet = { m ->
                                        pendingMeet = m
                                        showSwimmerLink = false
                                        showMeetConnect = true
                                    },
                                    onDismiss = { showSwimmerLink = false },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    composable("captures") {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(memScroll)
                                .padding(horizontal = 18.dp)
                                .padding(top = 12.dp, bottom = 108.dp),
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
                                else MemoryStore.semanticSearch(context, memoryQuery)
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
                // Full-screen image viewer overlay (above tab content, below nav bar).
                if (viewerFile != null) {
                    Box(Modifier.matchParentSize()) {
                        ImageViewerDialog(file = viewerFile!!, onClose = { viewerFile = null })
                    }
                }
            }

            // Floating icon-only bar overlaid on content: page content scrolls
            // behind it (each destination leaves bottom clearance), the bar
            // itself is translucent so the content shows through at the edges.
            val selectedSb = SbDestination.entries.firstOrNull { SbDestination.routeOf(it) == currentRoute }
                ?: SbDestination.HOME
            SbBottomBar(
                selected = if (selectedSb.primary) selectedSb else SbDestination.HOME,
                onSelect = { nav(SbDestination.routeOf(it)) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
