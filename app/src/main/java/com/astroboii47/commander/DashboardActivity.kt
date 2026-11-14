package com.astroboii47.commander

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE, d MMM")
private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy")
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a")
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

@Composable
private fun DashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }

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

    fun reloadFeeds() {
        feeds.clear()
        feeds.addAll(ICalStore.list(context))
        feedsVersion++
    }

    fun reloadCachedPs() {
        val cached = PowerSchoolStore.loadCourses(context)
        psCourses = cached
        if (cached.isNotEmpty()) {
            val terms = psCourseTerms(cached)
            if (selectedTerm == null || selectedTerm !in terms) {
                selectedTerm = terms.maxByOrNull { t -> cached.sumOf { c -> if (c.term == t) c.assignments.size else 0 } }
                selectedTerm?.let { PowerSchoolStore.setSelectedTerm(context, it) }
            }
            psUi = PsUi.Ready(cached, PowerSchoolStore.refreshedAt(context), stale = true)
        }
    }

    LaunchedEffect(Unit) {
        reloadFeeds()
        if (PowerSchoolStore.username(context) != null && PowerSchoolStore.loadCourses(context).isNotEmpty()) {
            reloadCachedPs()
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
                withContext(Dispatchers.IO) { PowerSchoolStore.saveCourses(context, normalized, stamp) }
                if (account != null) PowerSchoolStore.setUsername(context, account)
                psCourses = normalized
                psRefreshedAt = stamp
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
    val dotsByDay = remember(icalOccurrences, psCourses) {
        val map = mutableMapOf<LocalDate, Pair<Int, Int>>() // ical, ps-due
        for (o in icalOccurrences) {
            val cur = map[o.date] ?: Pair(0, 0)
            map[o.date] = Pair(cur.first + 1, cur.second)
        }
        for (a in PowerSchoolStore.distinctAssignments(psCourses)) {
            val d = a.dueIso?.let { psIsoToLocalDate(it) } ?: continue
            val cur = map[d] ?: Pair(0, 0)
            map[d] = Pair(cur.first, cur.second + 1)
        }
        map
    }
    val selectedAgenda = remember(selectedDay, icalOccurrences, psCourses) {
        val ical = icalOccurrences.filter { it.date == selectedDay }
        Pair(ical, PowerSchoolStore.dueOn(psCourses, selectedDay))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Houston", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    context.startActivity(
                        Intent(OverlayActivity.ACTION_OPEN_OVERLAY)
                            .setClass(context, OverlayActivity::class.java),
                    )
                }) {
                    Icon(Icons.Outlined.Search, "Open command bar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                    Icon(Icons.Outlined.Settings, "Houston settings", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Calendar
            DashboardCalendarCard(
                month = visibleMonth,
                selectedDay = selectedDay,
                today = today,
                dots = dotsByDay,
                onPrev = { visibleMonth = visibleMonth.minusMonths(1) },
                onNext = { visibleMonth = visibleMonth.plusMonths(1) },
                onToday = { visibleMonth = YearMonth.now(); selectedDay = LocalDate.now(); today = LocalDate.now() },
                onSelect = { selectedDay = it },
            )
            Spacer(Modifier.height(12.dp))
            AgendaCard(
                day = selectedDay,
                ical = selectedAgenda.first,
                ps = selectedAgenda.second,
                onOpenPs = { /* scroll handled below via term filter */ },
            )
            Spacer(Modifier.height(20.dp))

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
                            "No calendars yet. Add an iCal link — e.g. a Google Calendar “Secret address in iCal format” — and school events appear above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    feeds.forEach { feed ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(feed.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    feed.lastError ?: feed.url.take(64),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (feed.lastError != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                feed.lastSync?.let {
                                    Text(
                                        "Synced ${it.take(10)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                        OutlinedButton(onClick = { showAddFeed = true }, Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add iCal link")
                        }
                        OutlinedButton(
                            onClick = { refreshAllFeeds() },
                            enabled = feeds.isNotEmpty() && !syncingFeeds,
                            modifier = Modifier.weight(1f),
                        ) {
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
            Spacer(Modifier.height(20.dp))

            // PowerSchool
            SectionHeader(Icons.Outlined.School, "School")
            PowerSchoolCard(
                psUi = psUi,
                progress = psProgress,
                courses = psCourses,
                refreshedAt = psRefreshedAt,
                username = psUser,
                password = psPassword,
                server = psServer,
                showPassword = psShowPassword,
                selectedTerm = selectedTerm,
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
                    psUi = PsUi.SignedOut
                },
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader(Icons.Outlined.Shield, "Permissions")
            PermissionsCard()
            Spacer(Modifier.height(12.dp))
            Text(
                "PowerSchool signs in directly over HTTPS and keeps only session cookies on this phone — the password is never stored. Official grades shown are the portal's own; assignment averages are never presented as grades.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
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
}

private const val AGENDaExtra: Long = AGENDA_WINDOW_DAYS

private fun psUserMessage(e: PsException): String = when (e) {
    is PsException.InvalidCredentials -> "PowerSchool rejected the username or password."
    is PsException.SessionExpired -> "PowerSchool session expired — sign in again."
    is PsException.MfaRequired -> e.message ?: "PowerSchool asked for a verification code this app can't complete."
    is PsException.SsoRequired -> "PowerSchool redirected to a school login page this app can't complete."
    is PsException.Parse -> "Couldn't read a PowerSchool page (${(e.message ?: "").take(120)})."
    is PsException.Network -> "Network trouble talking to PowerSchool${e.status?.let { " (HTTP $it)" } ?: ""}."
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp),
        )
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun DashboardCalendarCard(
    month: YearMonth,
    selectedDay: LocalDate,
    today: LocalDate,
    dots: Map<LocalDate, Pair<Int, Int>>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1 // Monday-first
    val cells = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    val padded = cells + List((7 - cells.size % 7) % 7) { null }
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrev) { Text("‹", fontSize = 20.sp) }
                Text(
                    month.format(MONTH_TITLE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onNext) { Text("›", fontSize = 20.sp) }
                TextButton(onClick = onToday) { Text("Today", fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                    Text(
                        d, modifier = Modifier.weight(1f).padding(vertical = 3.dp),
                        textAlign = TextAlign.Center, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            padded.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val selected = date == selectedDay
                        val isToday = date == today
                        val dot = date?.let { dots[it] }
                        Box(
                            Modifier.weight(1f).height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .28f)
                                    else Color.Transparent,
                                )
                                .border(
                                    if (selected || isToday) 1.dp else 0.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp),
                                )
                                .then(if (date != null) Modifier.clickable { onSelect(date) } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (dot != null && (dot.first > 0 || dot.second > 0)) {
                                    Row(
                                        Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        if (dot.first > 0) Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                                        if (dot.second > 0) Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Teal dot = calendar event · red dot = assignment due",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun AgendaCard(
    day: LocalDate,
    ical: List<ICalOccurrence>,
    ps: List<PsAssignment>,
    onOpenPs: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(day.format(DAY_HEADER), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (ical.isEmpty() && ps.isEmpty()) {
                Text(
                    "Nothing on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ical.forEach { o ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.summary, style = MaterialTheme.typography.bodyLarge)
                        val when_ = when {
                            o.allDay -> "All day · ${o.feedName}"
                            o.startTime != null && o.endTime != null && o.startTime != o.endTime ->
                                "${o.startTime.format(TIME_FMT)} – ${o.endTime.format(TIME_FMT)} · ${o.feedName}"
                            o.startTime != null -> "${o.startTime.format(TIME_FMT)} · ${o.feedName}"
                            else -> o.feedName
                        }
                        Text(when_, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        o.location?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            ps.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (a.status == "missing") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name ?: "Assignment", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            buildString {
                                append(a.courseName)
                                a.category?.let { append(" · $it") }
                                val s = psScoreText(a)
                                if (s.isNotEmpty()) append(" · $s")
                                else if (a.percent != null) append(" · ${a.percent}%")
                                if (a.letter != null) append(" ${a.letter}")
                                if (a.status == "missing") append(" · MISSING")
                                else if (a.status == "late") append(" · late")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (a.status == "missing") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (ps.isNotEmpty()) {
                TextButton(onClick = onOpenPs) { Text("See all in School below") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PowerSchoolCard(
    psUi: PsUi,
    progress: String,
    courses: List<PsCourse>,
    refreshedAt: String?,
    username: String,
    password: String,
    server: String,
    showPassword: Boolean,
    selectedTerm: String?,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onServer: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onTerm: (String) -> Unit,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            when (psUi) {
                is PsUi.SignedOut -> {
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
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(server, onServer, label = { Text("Server") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onTogglePassword) { Text(if (showPassword) "Hide password" else "Show password") }
                        Button(onClick = onSignIn, enabled = username.isNotBlank() && password.isNotEmpty()) { Text("Sign in & sync") }
                    }
                }
                is PsUi.SigningIn -> {
                    Text(psUi.note.ifEmpty { "Working…" }, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (progress.isNotEmpty()) {
                        Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is PsUi.Ready -> {
                    PsGradesBody(
                        courses = courses, selectedTerm = selectedTerm, refreshedAt = refreshedAt,
                        syncing = false, onTerm = onTerm, onRefresh = onRefresh, onSignOut = onSignOut,
                    )
                }
                is PsUi.Failure -> {
                    Text(psUi.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    if (psUi.cached.isNotEmpty()) {
                        PsGradesBody(
                            courses = courses.ifEmpty { psUi.cached }, selectedTerm = selectedTerm,
                            refreshedAt = refreshedAt, syncing = false, onTerm = onTerm,
                            onRefresh = onRefresh, onSignOut = onSignOut,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRefresh) { Text("Retry") }
                            TextButton(onClick = onSignOut) { Text("Sign out") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PsGradesBody(
    courses: List<PsCourse>,
    selectedTerm: String?,
    refreshedAt: String?,
    syncing: Boolean,
    onTerm: (String) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
) {
    val terms = remember(courses) { psCourseTerms(courses) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Grades", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh PowerSchool", tint = MaterialTheme.colorScheme.primary) }
    }
    refreshedAt?.take(10)?.let {
        Text("Synced $it · official portal grades", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (terms.size > 1) {
        @OptIn(ExperimentalMaterial3Api::class)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            terms.forEach { t ->
                FilterChip(selected = t == selectedTerm, onClick = { onTerm(t) }, label = { Text(t) })
            }
        }
    }
    val shown = remember(courses, selectedTerm) {
        val t = selectedTerm
        (if (t == null) courses else courses.filter { it.term == t }).sortedBy { it.name.lowercase() }
    }
    if (shown.isEmpty()) {
        Text("No courses for this term yet — refresh to sync.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    shown.forEach { c ->
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    val sub = listOfNotNull(c.teacher, c.expression).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val counts = c.assignments.groupingBy { it.status }.eachCount()
                    val missing = counts["missing"] ?: 0
                    if (missing > 0) {
                        Text(
                            "$missing missing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        c.gradeLetter ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (c.gradeLetter == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else gradeColor(c.gradeLetter),
                    )
                    c.gradePercent?.let {
                        Text(
                            "${if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Needs attention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val attention = remember(courses, selectedTerm) {
        PowerSchoolStore.distinctAssignments(courses.filter { selectedTerm == null || it.term == selectedTerm })
            .filter { a -> a.status == "missing" || a.status == "late" || a.status == "ungraded" }
            .sortedWith(compareBy({ a: PsAssignment -> if (a.status == "missing") 0 else 1 }, { it.dueIso ?: "9999" }))
            .take(12)
    }
    if (attention.isEmpty()) {
        Text("All clear — nothing missing or late.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    attention.forEach { a ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (a.status == "missing") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name ?: "Assignment", style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append(a.courseName)
                        a.dueMdy?.let { append(" · due $it") }
                        val s = psScoreText(a)
                        if (s.isNotEmpty()) append(" · $s") else append(" · ${a.status}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.status == "missing") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}

private fun psScoreText(a: PsAssignment): String {
    val e = a.earned ?: return ""
    val p = a.possible ?: return ""
    fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    return "${fmt(e)}/${fmt(p)}"
}

@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    var probe by remember { mutableIntStateOf(0) }
    val granted = remember(probe) { NotificationSearchSettings.hasAccess(context) }
    val liveCount = remember(probe, granted) {
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
            IconButton(onClick = { probe++ }) {
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
private fun gradeColor(letter: String): Color {
    return when (letter.uppercase().firstOrNull()) {
        'A' -> Color(0xFF2E7D32)
        'B' -> Color(0xFF558B2F)
        'C' -> Color(0xFFF9A825)
        'D', 'F' -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
}
