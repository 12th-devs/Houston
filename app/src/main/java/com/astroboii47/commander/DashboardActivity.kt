package com.astroboii47.commander

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
        setContent { DashboardTheme { DashboardScreen() } }
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

private enum class DashTab { HOME, CALENDAR, SCHOOL }

@Composable
private fun DashboardScreen() {
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

    // Swim state
    var swim by remember { mutableStateOf(SwimStore.load(context)) }
    var swimExpanded by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        reloadFeeds()
        if (PowerSchoolStore.username(context) != null && PowerSchoolStore.loadCourses(context).isNotEmpty()) {
            reloadCachedPs()
        }
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Full-bleed header: waves run edge to edge behind the status bar.
            Box(Modifier.fillMaxWidth()) {
                HeaderWaves(Modifier.fillMaxWidth().height(172.dp))
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
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    DashTab.HOME -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                        ) {
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
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    DashTab.CALENDAR -> {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
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
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .navigationBarsPadding()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
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
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            NavigationBar {
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
                    selected = false,
                    onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                    icon = { Icon(Icons.Outlined.Settings, "Settings") },
                    label = { Text("Settings") },
                )
            }
        }
    }
}
