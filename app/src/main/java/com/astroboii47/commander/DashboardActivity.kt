package com.astroboii47.commander

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy")
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a")
private val AGENDaExtra: Long = 45L
private const val AGENDA_WINDOW_DAYS = 45L

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

private sealed interface PsUi {
    data object SignedOut : PsUi
    data class SigningIn(val note: String) : PsUi
    data class Ready(val courses: List<PsCourse>, val refreshedAt: String?, val stale: Boolean) : PsUi
    data class Failure(val message: String, val cached: List<PsCourse>) : PsUi
}

private enum class DashTab { HOME, CALENDAR, SCHOOL, SETTINGS }

@Composable
private fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var tab by remember { mutableStateOf(DashTab.HOME) }
    var calendarY by remember { mutableStateOf(0) }
    var schoolY by remember { mutableStateOf(0) }

    fun scrollTo(px: Int) {
        scope.launch { scroll.animateScrollTo(px.coerceAtLeast(0)) }
    }

    // iCal state
    val feeds = remember { mutableStateListOf<ICalFeed>() }
    var feedsVersion by remember { mutableIntStateOf(0) }
    var showAddFeed by remember { mutableStateOf(false) }
    var syncingFeeds by remember { mutableStateOf(false) }

    // PowerSchool state
    var psUi by remember { mutableStateOf<PsUi>(PsUi.SignedOut) }
    var psProgress by remember { mutableStateOf("") }
    var psUser by remember { mutableStateOf(PowerSchoolStore.username(context).orEmpty()) }
    var psPassword by remember { mutableStateOf("") }
    var psServer by remember { mutableStateOf(PowerSchoolStore.baseUrl(context)) }
    var psShowPassword by remember { mutableStateOf(false) }
    var selectedTerm by remember { mutableStateOf(PowerSchoolStore.selectedTerm(context)) }
    var psCourses by remember { mutableStateOf(listOf<PsCourse>()) }
    var psRefreshedAt by remember { mutableStateOf(PowerSchoolStore.refreshedAt(context)) }
    var gradeHist by remember { mutableStateOf(listOf<PowerSchoolStore.GradeSnapshot>()) }
    var schoolExpanded by remember { mutableStateOf(false) }

    // Weather + swim + updates
    var wx by remember { mutableStateOf(WeatherStore.cached(context)) }
    var showLocation by remember { mutableStateOf(false) }
    var swim by remember { mutableStateOf(SwimStore.load(context)) }
    var showSwimTargets by remember { mutableStateOf(false) }
    var showSwimLog by remember { mutableStateOf(false) }
    var swimExpanded by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<UpdateManager.CheckResult?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var dlProgress by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var notifProbe by remember { mutableIntStateOf(0) }

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
            psUi = PsUi.Ready(cached, PowerSchoolStore.refreshedAt(context), stale = true)
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        if (checkingUpdate) return
        scope.launch {
            checkingUpdate = true
            try {
                val result = withContext(Dispatchers.IO) { UpdateManager.check(context) }
                updateState = result
                if (!silent && result is UpdateManager.CheckResult.Failed) {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                checkingUpdate = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadFeeds()
        if (PowerSchoolStore.username(context) != null && PowerSchoolStore.loadCourses(context).isNotEmpty()) {
            reloadCachedPs()
        }
        checkForUpdates(silent = true)
        if (WeatherStore.needsRefresh(context, LocalDate.now())) {
            val fresh = withContext(Dispatchers.IO) { WeatherStore.refresh(context) }
            if (fresh != null) wx = fresh
        }
    }

    fun doPsRefresh(account: String?, password: String?) {
        scope.launch {
            psProgress = "Connecting…"
            psUi = PsUi.SigningIn("Connecting…")
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
                psUi = PsUi.Ready(normalized, stamp, stale = false)
            } catch (e: PsException) {
                val cached = PowerSchoolStore.loadCourses(context)
                psCourses = cached
                gradeHist = PowerSchoolStore.gradeHistory(context)
                psUi = if (e is PsException.SessionExpired && cached.isEmpty()) {
                    PsUi.SignedOut
                } else {
                    PsUi.Failure(psUserMessage(e), cached)
                }
                if (e !is PsException.SessionExpired || cached.isEmpty()) {
                    Toast.makeText(context, psUserMessage(e), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                val cached = PowerSchoolStore.loadCourses(context)
                psCourses = cached
                gradeHist = PowerSchoolStore.gradeHistory(context)
                psUi = PsUi.Failure("Refresh failed: ${(e.message ?: "unknown error").take(160)}", cached)
            }
        }
    }

    fun refreshAllFeeds() {
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

    // Occurrences for the visible month + agenda window.
    val monthStart = remember(visibleMonth) { visibleMonth.atDay(1) }
    val monthEnd = remember(visibleMonth) { visibleMonth.atEndOfMonth() }
    val windowStart = remember(monthStart) { monthStart.minusWeeks(1) }
    val windowEnd = remember(monthEnd) { monthEnd.plusDays(AGENDaExtra) }
    val icalOccurrences = remember(feedsVersion, windowStart, windowEnd) {
        runCatching { ICalStore.occurrences(context, windowStart, windowEnd) }.getOrDefault(emptyList())
    }
    val feedColorIdx = remember(feeds) { feeds.mapIndexed { i, f -> f.id to (i % 3) }.toMap() }
    val feedPalette = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
    val schoolDot = MaterialTheme.colorScheme.secondary
    fun feedColor(id: String): Color = feedPalette[(feedColorIdx[id] ?: 0) % feedPalette.size]
    val dotsByDay = remember(icalOccurrences, psCourses) {
        val map = mutableMapOf<LocalDate, MutableList<Color>>()
        for (o in icalOccurrences) {
            map.getOrPut(o.date) { mutableListOf() }.add(feedColor(o.feedId))
        }
        for (a in PowerSchoolStore.distinctAssignments(psCourses)) {
            val d = a.dueIso?.let { psIsoToLocalDate(it) } ?: continue
            map.getOrPut(d) { mutableListOf() }.add(schoolDot)
        }
        map
    }
    val selectedIcal = remember(selectedDay, icalOccurrences) {
        icalOccurrences.filter { it.date == selectedDay }
    }
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
    fun moverDate(courseId: String, term: String, current: Double): String? {
        val key = "$courseId|$term"
        var seen: Double? = null
        var firstWithNew: String? = null
        for (s in gradeHist) {
            val v = s.percents[key]
            if (v != null && v != seen) {
                if (seen != null && v == current) firstWithNew = s.date
                seen = v
            }
        }
        return firstWithNew
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).verticalScroll(scroll).navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                // Header with material waves
                Box(Modifier.fillMaxWidth()) {
                    HeaderWaves(Modifier.fillMaxWidth().height(150.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WaveformMark()
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Houston", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text(
                                today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(OverlayActivity.ACTION_OPEN_OVERLAY)
                                        .setClass(context, OverlayActivity::class.java),
                                )
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), CircleShape),
                        ) {
                            Icon(Icons.Outlined.Search, "Open command bar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), CircleShape),
                        ) {
                            Icon(Icons.Outlined.Settings, "Houston settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                UpdateCard(
                    state = updateState,
                    checking = checkingUpdate,
                    downloading = downloading,
                    progress = dlProgress,
                    onCheck = { checkForUpdates(silent = false) },
                    onDownload = { info ->
                        if (downloading) return@UpdateCard
                        scope.launch {
                            downloading = true
                            dlProgress = Pair(0L, -1L)
                            try {
                                val apk = withContext(Dispatchers.IO) {
                                    UpdateManager.download(context, info) { done, total -> dlProgress = Pair(done, total) }
                                }
                                updateState = UpdateManager.CheckResult.Available(info, apk)
                                if (!UpdateManager.canInstall(context)) {
                                    Toast.makeText(context, "Allow Houston to install apps, then tap Install.", Toast.LENGTH_LONG).show()
                                    UpdateManager.openUnknownSourcesSettings(context)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, (e.message ?: "Download failed.").take(200), Toast.LENGTH_LONG).show()
                            } finally {
                                downloading = false
                                dlProgress = null
                            }
                        }
                    },
                    onInstall = { apk ->
                        runCatching { UpdateManager.install(context, apk) }.onFailure {
                            Toast.makeText(context, "Couldn't open installer: ${it.message?.take(120)}", Toast.LENGTH_LONG).show()
                        }
                    },
                )

                // Calendar
                Box(Modifier.onGloballyPositioned { calendarY = it.positionInParent().y.toInt() }) {
                    DashboardCalendarCard(
                        month = visibleMonth,
                        selectedDay = selectedDay,
                        today = today,
                        dots = dotsByDay,
                        legend = remember(feeds, feedColorIdx) {
                            feeds.map { it.name to feedPalette[(feedColorIdx[it.id] ?: 0) % feedPalette.size] } + listOf("School" to schoolDot)
                        },
                        onPrev = { visibleMonth = visibleMonth.minusMonths(1) },
                        onNext = { visibleMonth = visibleMonth.plusMonths(1) },
                        onToday = { visibleMonth = YearMonth.now(); selectedDay = LocalDate.now(); today = LocalDate.now() },
                        onSelect = { selectedDay = it },
                    )
                }
                Spacer(Modifier.height(12.dp))

                YourDayBanner(
                    day = selectedDay,
                    wx = wx,
                    onWeatherClick = { showLocation = true },
                )
                Spacer(Modifier.height(12.dp))

                // Calendar events (selected day, iCal only)
                SectionCard(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Calendar Events",
                    onGlobalY = {},
                ) {
                    if (selectedIcal.isEmpty()) {
                        Text(
                            "Nothing on ${selectedDay.format(DateTimeFormatter.ofPattern("EEE, MMM d"))}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    selectedIcal.forEach { o ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (o.allDay) "All day"
                                else if (o.startTime != null && o.endTime != null && o.startTime != o.endTime)
                                    "${o.startTime.format(TIME_FMT)} – ${o.endTime.format(TIME_FMT)}"
                                else o.startTime?.format(TIME_FMT) ?: o.feedName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            Box(
                                Modifier.padding(horizontal = 10.dp).width(4.dp).height(40.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(feedColor(o.feedId)),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(o.summary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                o.location?.takeIf { it.isNotBlank() }?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Text(o.feedName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // School
                Box(Modifier.onGloballyPositioned { schoolY = it.positionInParent().y.toInt() }) {
                    SchoolCard(
                        psUi = psUi,
                        progress = psProgress,
                        courses = termCourses,
                        allCourses = psCourses,
                        refreshedAt = psRefreshedAt,
                        username = psUser,
                        password = psPassword,
                        server = psServer,
                        showPassword = psShowPassword,
                        selectedTerm = selectedTerm,
                        terms = remember(psCourses) { psCourseTerms(psCourses) },
                        gpa = gpa,
                        gpaDelta = gpaDelta,
                        letterCounts = letterCounts,
                        movers = movers,
                        moverDate = { m -> moverDate(m.courseId, selectedTerm ?: termCourses.firstOrNull { it.id == m.courseId }?.term.orEmpty(), m.newPercent) },
                        gradeHistSize = gradeHist.size,
                        expanded = schoolExpanded,
                        onToggleExpanded = { schoolExpanded = !schoolExpanded },
                        onUsername = { psUser = it },
                        onPassword = { psPassword = it },
                        onServer = { psServer = it },
                        onTogglePassword = { psShowPassword = !psShowPassword },
                        onTerm = {
                            selectedTerm = it
                            PowerSchoolStore.setSelectedTerm(context, it)
                        },
                        onSignIn = {
                            PowerSchoolStore.setBaseUrl(context, psServer.ifBlank { PS_DEFAULT_BASE_URL })
                            doPsRefresh(psUser.trim().ifEmpty { null }, psPassword.ifEmpty { null })
                        },
                        onRefresh = { doPsRefresh(null, null) },
                        onSignOut = {
                            PowerSchoolStore.clearAccount(context)
                            psCourses = emptyList()
                            psPassword = ""
                            psUser = ""
                            selectedTerm = null
                            gradeHist = emptyList()
                            psUi = PsUi.SignedOut
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Swimming (local training log)
                SwimCard(
                    state = swim,
                    onEditTargets = { showSwimTargets = true },
                    onAddEntry = { showSwimLog = true },
                    onDeleteEntry = {
                        SwimStore.removeEntry(context, it)
                        swim = SwimStore.load(context)
                    },
                    expanded = swimExpanded,
                    onToggleExpanded = { swimExpanded = !swimExpanded },
                )
                Spacer(Modifier.height(16.dp))

                // iCal feeds
                SectionHeader(Icons.Outlined.CalendarMonth, "Calendars")
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                        if (feeds.isEmpty()) {
                            Text(
                                "No calendars yet. Add an iCal link — e.g. a Google Calendar “Secret address in iCal format” — and events appear above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        feeds.forEach { feed ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(feedColor(feed.id)))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(feed.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        feed.lastError ?: feed.url.take(64),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (feed.lastError != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    feed.lastSync?.let {
                                        Text("Synced ${it.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (syncingFeeds) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = {
                                        scope.launch {
                                            syncingFeeds = true
                                            try {
                                                withContext(Dispatchers.IO) { ICalStore.refresh(context, feed) }
                                            } finally {
                                                reloadFeeds(); syncingFeeds = false
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Outlined.Sync, "Sync ${feed.name}", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { ICalStore.remove(context, feed.id); reloadFeeds() }) {
                                        Icon(Icons.Outlined.Delete, "Remove ${feed.name}", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showAddFeed = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add iCal link")
                            }
                            OutlinedButton(onClick = { refreshAllFeeds() }, enabled = feeds.isNotEmpty() && !syncingFeeds, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync all")
                            }
                        }
                        Text(
                            "Only UNTIL/COUNT daily and weekly repeats expand; monthly rules and time-zone files are skipped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                SectionHeader(Icons.Outlined.Shield, "Permissions")
                PermissionsCard(notifProbe = notifProbe, onRecheck = { notifProbe++ })
                Spacer(Modifier.height(12.dp))
                Text(
                    "PowerSchool signs in directly over HTTPS and keeps only session cookies on this phone — the password is never stored. Official grades shown are the portal's own; assignment averages are never presented as grades.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val versionName = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) {
                        null
                    }
                    Text(
                        "Houston ${versionName ?: ""}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { checkForUpdates(silent = false) }, enabled = !checkingUpdate) {
                        Text(if (checkingUpdate) "Checking…" else "Check for updates")
                    }
                }
            }
            // Bottom nav
            DashBottomBar(
                tab = tab,
                onTab = {
                    tab = it
                    when (it) {
                        DashTab.HOME -> scrollTo(0)
                        DashTab.CALENDAR -> scrollTo(calendarY)
                        DashTab.SCHOOL -> scrollTo(schoolY)
                        DashTab.SETTINGS -> context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                },
            )
        }
    }

    if (showAddFeed) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var testing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!testing) showAddFeed = false },
            title = { Text("Add iCal link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it; error = null }, label = { Text("Name (e.g. School)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it; error = null }, label = { Text("iCal URL (https://…)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !testing && url.isNotBlank(),
                    onClick = {
                        scope.launch {
                            testing = true
                            error = null
                            try {
                                val feed = withContext(Dispatchers.IO) {
                                    val f = ICalStore.add(context, name.ifBlank { url }, url)
                                    val r = ICalStore.refresh(context, f)
                                    r.getOrThrow()
                                    f
                                }
                                reloadFeeds()
                                showAddFeed = false
                                Toast.makeText(context, "${feed.name} added", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                error = (e.message ?: "Could not add this link.").take(220)
                            } finally {
                                testing = false
                            }
                        }
                    },
                ) { Text(if (testing) "Checking…" else "Add") }
            },
            dismissButton = { TextButton(onClick = { showAddFeed = false }) { Text("Cancel") } },
        )
    }

    if (showLocation) {
        var lat by remember { mutableStateOf(WeatherStore.lat(context).toString()) }
        var lon by remember { mutableStateOf(WeatherStore.lon(context).toString()) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showLocation = false },
            title = { Text("Weather location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Latitude and longitude for the forecast. Defaults to Louisville, KY.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(lat, { lat = it; error = null }, label = { Text("Latitude") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(lon, { lon = it; error = null }, label = { Text("Longitude") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val la = lat.toDoubleOrNull()
                    val lo = lon.toDoubleOrNull()
                    if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0) {
                        error = "Enter a valid latitude (-90…90) and longitude (-180…180)."
                    } else {
                        WeatherStore.setLocation(context, la, lo)
                        showLocation = false
                        scope.launch {
                            withContext(Dispatchers.IO) { WeatherStore.refresh(context) }?.let { wx = it }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLocation = false }) { Text("Cancel") } },
        )
    }

    if (showSwimTargets) {
        var eventName by remember { mutableStateOf(swim.eventName) }
        var start by remember { mutableStateOf(swim.baselineSec?.let { formatSwimTime(it) } ?: "") }
        var best by remember { mutableStateOf(swim.bestSec?.let { formatSwimTime(it) } ?: "") }
        var goal by remember { mutableStateOf(swim.goalSec?.let { formatSwimTime(it) } ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSwimTargets = false },
            title = { Text("Swim targets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(eventName, { eventName = it }, label = { Text("Event (e.g. 100 Breast – SCY)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(start, { start = it; error = null }, label = { Text("Season start (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(best, { best = it; error = null }, label = { Text("Current best (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(goal, { goal = it; error = null }, label = { Text("Goal (m:ss.cc)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = if (start.isBlank()) null else parseSwimTime(start)
                    val b = if (best.isBlank()) null else parseSwimTime(best)
                    val g = if (goal.isBlank()) null else parseSwimTime(goal)
                    if ((start.isNotBlank() && s == null) || (best.isNotBlank() && b == null) || (goal.isNotBlank() && g == null)) {
                        error = "Times look like 1:08.20 or 68.20."
                    } else {
                        SwimStore.saveTargets(context, eventName.ifBlank { "Swim" }, s, b, g)
                        swim = SwimStore.load(context)
                        showSwimTargets = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSwimTargets = false }) { Text("Cancel") } },
        )
    }

    if (showSwimLog) {
        var title by remember { mutableStateOf("") }
        var detail by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now().toString()) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSwimLog = false },
            title = { Text("Log practice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it; error = null }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(detail, { detail = it }, label = { Text("Detail (e.g. 6,800 yd · Endurance)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it; error = null }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) {
                        error = "Give the entry a title."
                    } else if (psIsoToLocalDate(date) == null) {
                        error = "Date must look like 2026-11-14."
                    } else {
                        SwimStore.addEntry(context, date, title, detail.ifBlank { null })
                        swim = SwimStore.load(context)
                        showSwimLog = false
                    }
                }) { Text("Log") }
            },
            dismissButton = { TextButton(onClick = { showSwimLog = false }) { Text("Cancel") } },
        )
    }
}

private fun psUserMessage(e: PsException): String = when (e) {
    is PsException.InvalidCredentials -> "PowerSchool rejected the username or password."
    is PsException.SessionExpired -> "PowerSchool session expired — sign in again."
    is PsException.MfaRequired -> e.message ?: "PowerSchool asked for a verification code this app can't complete."
    is PsException.SsoRequired -> "PowerSchool redirected to a school login page this app can't complete."
    is PsException.Parse -> "Couldn't read a PowerSchool page (${(e.message ?: "").take(120)})."
    is PsException.Network -> "Network trouble talking to PowerSchool${e.status?.let { " (HTTP $it)" } ?: ""}."
}

private fun relDayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}

// ---------- Header art (dynamic color, never fixed purple) ----------

@Composable
private fun HeaderWaves(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawPath(
            Path().apply {
                moveTo(w * 0.42f, 0f)
                cubicTo(w * 0.62f, h * 0.25f, w * 0.55f, h * 0.55f, w * 0.78f, h * 0.72f)
                cubicTo(w * 0.95f, h * 0.85f, w * 0.98f, h, w, h)
                lineTo(w, 0f)
                close()
            },
            color = primary.copy(alpha = .16f),
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.66f, 0f)
                cubicTo(w * 0.8f, h * 0.3f, w * 0.78f, h * 0.6f, w, h * 0.82f)
                lineTo(w, 0f)
                close()
            },
            color = tertiary.copy(alpha = .20f),
        )
        drawPath(
            Path().apply {
                moveTo(w * 0.88f, 0f)
                cubicTo(w * 0.94f, h * 0.35f, w * 0.93f, h * 0.6f, w, h)
                lineTo(w, 0f)
                close()
            },
            color = primary.copy(alpha = .22f),
        )
    }
}

@Composable
private fun WaveformMark(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val bars = listOf(0.35f, 0.7f, 1f, 0.6f, 0.85f)
    val cols = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.tertiary, scheme.primary)
    Canvas(modifier.size(34.dp, 30.dp)) {
        val bw = size.width / (bars.size * 2f)
        bars.forEachIndexed { i, f ->
            val h = size.height * f
            drawRoundRect(
                color = cols[i % cols.size],
                topLeft = Offset(i * 2 * bw + bw * 0.25f, (size.height - h) / 2),
                size = androidx.compose.ui.geometry.Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2, bw / 2),
            )
        }
    }
}

// ---------- Calendar ----------

@Composable
private fun DashboardCalendarCard(
    month: YearMonth,
    selectedDay: LocalDate,
    today: LocalDate,
    dots: Map<LocalDate, List<Color>>,
    legend: List<Pair<String, Color>>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7 // Sunday-first
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev) { Text("‹", fontSize = 22.sp) }
                Text(
                    month.format(MONTH_TITLE),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onNext) { Text("›", fontSize = 22.sp) }
                Button(
                    onClick = onToday,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("Today", fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(
                        d, modifier = Modifier.weight(1f).padding(vertical = 5.dp),
                        textAlign = TextAlign.Center, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            padded.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val selected = date == selectedDay
                        val isToday = date != null && date == today
                        val dayDots = date?.let { dots[it] }.orEmpty().take(3)
                        Box(
                            Modifier.weight(1f).height(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    when {
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = .18f)
                                        else -> Color.Transparent
                                    },
                                )
                                .border(
                                    if (selected && !isToday) 1.dp else 0.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(13.dp),
                                )
                                .then(if (date != null) Modifier.clickable { onSelect(date) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        fontSize = 13.sp,
                                        fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                                        dayDots.forEach { c ->
                                            Box(Modifier.size(4.dp).clip(CircleShape).background(c))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (legend.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    legend.take(4).forEachIndexed { i, (name, color) ->
                        if (i > 0) Spacer(Modifier.width(14.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(5.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------- Your Day banner + weather ----------

@Composable
private fun YourDayBanner(day: LocalDate, wx: WxToday?, onWeatherClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Your Day", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(Modifier.clickable(onClick = onWeatherClick), verticalAlignment = Alignment.CenterVertically) {
                WxIcon(wx?.code)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (wx != null) "${wx.hiF.toInt()}° / ${wx.loF.toInt()}°F" else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun WxIcon(code: Int?) {
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    val icon = when (wxKind(code ?: 3)) {
        "sunny" -> Icons.Outlined.WbSunny
        "rain", "drizzle" -> Icons.Outlined.WaterDrop
        "snow" -> Icons.Outlined.AcUnit
        "storm" -> Icons.Outlined.Thunderstorm
        else -> Icons.Outlined.Cloud
    }
    Icon(icon, "Weather", tint = tint, modifier = Modifier.size(34.dp))
}

// ---------- Generic section card ----------

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    viewAllLabel: String? = null,
    onViewAll: (() -> Unit)? = null,
    onGlobalY: (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().onGloballyPositioned { onGlobalY(it.positionInParent().y.toInt()) },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (viewAllLabel != null && onViewAll != null) {
                    OutlinedButton(
                        onClick = onViewAll,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(viewAllLabel, fontSize = 13.sp)
                        Icon(Icons.Outlined.ChevronRight, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
}

// ---------- School ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolCard(
    psUi: PsUi,
    progress: String,
    courses: List<PsCourse>,
    allCourses: List<PsCourse>,
    refreshedAt: String?,
    username: String,
    password: String,
    server: String,
    showPassword: Boolean,
    selectedTerm: String?,
    terms: List<String>,
    gpa: Double?,
    gpaDelta: Double?,
    letterCounts: List<Pair<String, Int>>,
    movers: List<PsGradeMove>,
    moverDate: (PsGradeMove) -> String?,
    gradeHistSize: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onServer: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onTerm: (String) -> Unit,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    when (psUi) {
        is PsUi.SignedOut -> SectionCard(icon = Icons.Outlined.School, title = "School") {
            Text("Connect PowerSchool", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Guardian sign-in for grades and assignments. Only session cookies stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            OutlinedTextField(username, onUsername, label = { Text("Parent username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                password, onPassword, label = { Text("Password (never stored)") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(server, onServer, label = { Text("Server") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTogglePassword) { Text(if (showPassword) "Hide password" else "Show password") }
                Button(onClick = onSignIn, enabled = username.isNotBlank() && password.isNotEmpty()) { Text("Sign in & sync") }
            }
        }
        is PsUi.SigningIn -> SectionCard(icon = Icons.Outlined.School, title = "School") {
            Text(psUi.note.ifEmpty { "Working…" }, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (progress.isNotEmpty()) {
                Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is PsUi.Ready, is PsUi.Failure -> {
            val err = (psUi as? PsUi.Failure)?.message
            SectionCard(
                icon = Icons.Outlined.School,
                title = "School",
                viewAllLabel = "View All",
                onViewAll = onToggleExpanded,
            ) {
                if (err != null) {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
                // GPA + classes
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("GPA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                gpa?.let { "%.2f".format(it) } ?: "—",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            if (gpaDelta != null && kotlin.math.abs(gpaDelta) >= 0.005) {
                                val up = gpaDelta > 0
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (up) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                                        null,
                                        tint = if (up) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        (if (up) "+" else "") + "%.2f".format(gpaDelta),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (up) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).height(52.dp).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterVertically))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Classes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(courses.size.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            letterCounts.forEach { (letter, n) ->
                                val c = gradeColor(letter)
                                Text(
                                    "$n ${letter}'s",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c,
                                    modifier = Modifier.background(c.copy(alpha = .16f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
                if (terms.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        terms.forEach { t ->
                            FilterChip(selected = t == selectedTerm, onClick = { onTerm(t) }, label = { Text(t) })
                        }
                    }
                }
                if (courses.isEmpty()) {
                    Text("No courses for this term yet — refresh to sync.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Movers + next upcoming
                val shown = remember(courses) { courses }
                MoverRows(courses = shown, movers = movers, moverDate = moverDate, expanded = expanded, allCourses = allCourses)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    refreshedAt?.take(10)?.let {
                        Text("Synced $it · portal grades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } ?: Spacer(Modifier.weight(1f))
                    Row {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, "Refresh PowerSchool", tint = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = onSignOut) { Text("Sign out") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoverRows(
    courses: List<PsCourse>,
    movers: List<PsGradeMove>,
    moverDate: (PsGradeMove) -> String?,
    expanded: Boolean,
    allCourses: List<PsCourse>,
) {
    val today = LocalDate.now()
    fun moverSubtitle(m: PsGradeMove): String {
        fun pct(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
        return if (m.delta < 0) "Dropped ${(-m.delta).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }}% (${pct(m.oldPercent)} → ${pct(m.newPercent)})"
        else "+${if (m.delta % 1.0 == 0.0) m.delta.toInt().toString() else "%.1f".format(m.delta)}% (${pct(m.oldPercent)} → ${pct(m.newPercent)})"
    }
    for (m in movers) {
        val down = m.delta < 0
        val dateLabel = moverDate(m)?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.let { d -> relDayLabel(d, today) } } ?: "Recently"
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (down) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (down) Icons.AutoMirrored.Outlined.TrendingDown else Icons.AutoMirrored.Outlined.TrendingUp,
                    null,
                    tint = if (down) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val courseName = courses.firstOrNull { it.id == m.courseId }?.name ?: m.courseName
                val letter = courses.firstOrNull { it.id == m.courseId }?.gradeLetter
                Text(
                    "$courseName${letter?.let { " — $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(moverSubtitle(m), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val nextUp = remember(allCourses) {
        PowerSchoolStore.upcoming(allCourses, today, 30)
            .filter { it.status != "missing" }
            .take(if (expanded) 40 else 2)
    }
    for (a in nextUp) {
        val due = a.dueIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (a.name ?: "Assignment") + " (${a.courseName})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Due ${due?.format(DateTimeFormatter.ofPattern("EEE, MMM d")) ?: (a.dueMdy ?: "undated")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Upcoming", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (movers.isEmpty() && nextUp.isEmpty()) {
        Text("All clear — nothing moving and nothing due.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Swimming (local log) ----------

@Composable
private fun SwimCard(
    state: SwimState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditTargets: () -> Unit,
    onAddEntry: () -> Unit,
    onDeleteEntry: (String) -> Unit,
) {
    val progress = swimProgressPct(state.baselineSec, state.bestSec, state.goalSec)
    SectionCard(icon = Icons.Outlined.Waves, title = "Swimming", viewAllLabel = "View All", onViewAll = onToggleExpanded) {
        Text(state.eventName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Current Best", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.bestSec?.let { formatSwimTime(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    state.goalSec?.let { formatSwimTime(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
            }
            if (state.bestSec != null && state.goalSec != null && state.bestSec > state.goalSec) {
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.TrendingDown, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("%.2fs".format(state.bestSec - state.goalSec), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text("To Go", fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { (progress ?: 0f).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                progress?.let { "${(it * 100).toInt()}%" } ?: "—",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        val entries = if (expanded) state.log else state.log.take(2)
        for (e in entries) {
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (e.title.contains("improv", true)) Icons.Outlined.ShowChart else Icons.Outlined.Group,
                        null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    e.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { relDayLabel(it) } ?: e.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { onDeleteEntry(e.id) }) {
                    Icon(Icons.Outlined.Delete, "Delete entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (state.log.isEmpty()) {
            Text("No entries yet — log a practice to start the history.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAddEntry, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log practice")
            }
            OutlinedButton(onClick = onEditTargets, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Targets")
            }
        }
    }
}

// ---------- Updates / permissions / footer ----------

@Composable
private fun UpdateCard(
    state: UpdateManager.CheckResult?,
    checking: Boolean,
    downloading: Boolean,
    progress: Pair<Long, Long>?,
    onCheck: () -> Unit,
    onDownload: (UpdateManager.UpdateInfo) -> Unit,
    onInstall: (java.io.File) -> Unit,
) {
    if ((state == null || state is UpdateManager.CheckResult.UpToDate) && !checking && !downloading) return
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("App update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            when {
                checking -> {
                    Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state is UpdateManager.CheckResult.Available -> {
                    val info = state.info
                    Text("v${info.versionName} is ready (${info.tag}).", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (info.notes.isNotBlank()) {
                        Text(info.notes.take(400), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    val (done, total) = progress ?: Pair(0L, -1L)
                    if (downloading) {
                        if (total > 0) {
                            LinearProgressIndicator(
                                progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${done / 1_048_576} / ${total / 1_048_576} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Downloading… ${done / 1_048_576} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val staged = state.downloaded
                        if (staged != null) {
                            Button(onClick = { onInstall(staged) }, modifier = Modifier.weight(1f)) { Text("Install") }
                        } else {
                            Button(onClick = { onDownload(info) }, enabled = !downloading, modifier = Modifier.weight(1f)) {
                                Text(if (downloading) "Downloading…" else "Download")
                            }
                        }
                        OutlinedButton(onClick = onCheck, enabled = !downloading, modifier = Modifier.weight(1f)) { Text("Re-check") }
                    }
                    Text(
                        "Updates install over this app — same signature, your data stays.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                state is UpdateManager.CheckResult.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onCheck) { Text("Try again") }
                }
                else -> {
                    Text("You're up to date.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(notifProbe: Int, onRecheck: () -> Unit) {
    val context = LocalContext.current
    val granted = remember(notifProbe) { NotificationSearchSettings.hasAccess(context) }
    val liveCount = remember(notifProbe, granted) {
        if (granted) NotificationSearchSettings.live(context).size else 0
    }
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Notification access", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (granted) "Granted · $liveCount live notifications visible"
                    else "Not granted · needed for player controls and live notification search",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onRecheck) {
                Icon(Icons.Outlined.Refresh, "Re-check access", tint = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text(if (granted) "Settings" else "Grant")
            }
        }
    }
}

@Composable
private fun DashBottomBar(tab: DashTab, onTab: (DashTab) -> Unit) {
    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            DashTabButton("Home", Icons.Outlined.Home, tab == DashTab.HOME) { onTab(DashTab.HOME) }
            DashTabButton("Calendar", Icons.Outlined.CalendarMonth, tab == DashTab.CALENDAR) { onTab(DashTab.CALENDAR) }
            DashTabButton("School", Icons.Outlined.School, tab == DashTab.SCHOOL) { onTab(DashTab.SCHOOL) }
            DashTabButton("Settings", Icons.Outlined.Settings, tab == DashTab.SETTINGS) { onTab(DashTab.SETTINGS) }
        }
    }
}

@Composable
private fun DashTabButton(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            label, fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun gradeColor(letter: String): Color {
    return when (letter.uppercase().firstOrNull()) {
        'A' -> Color(0xFF2E7D32)
        'B' -> Color(0xFF558B2F)
        'C' -> Color(0xFFF9A825)
        'D', 'F' -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
}
