package com.astroboii47.commander

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Minimal iCalendar import: single + recurring VEVENTs. Pure Kotlin, unit
 *  tested. Supports DATE and DATE-TIME (UTC `Z`, floating, TZID) starts,
 *  DTEND/DURATION ends, EXDATE, and RRULE FREQ=DAILY|WEEKLY with
 *  INTERVAL/COUNT/UNTIL/BYDAY. Anything fancier (MONTHLY/YEARLY, RDATE,
 *  RECURRENCE-ID overrides, VTIMEZONE definitions) is ignored by design and
 *  noted on the dashboard. */

data class ICalMoment(val date: LocalDate, val allDay: Boolean, val time: LocalTime?)

data class ICalRecurrence(
    val freq: String,
    val interval: Int = 1,
    val count: Int? = null,
    val until: LocalDate? = null,
    val byDay: Set<DayOfWeek> = emptySet(),
)

data class ICalEvent(
    val uid: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val start: ICalMoment,
    val end: ICalMoment?,
    val exDates: Set<LocalDate> = emptySet(),
    val recurrence: ICalRecurrence? = null,
)

data class ICalOccurrence(
    val uid: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val date: LocalDate,
    val allDay: Boolean,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val feedId: String,
    val feedName: String,
)

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
private val DATETIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

fun icalUnescape(s: String): String {
    val b = StringBuilder()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) {
            when (s[i + 1]) {
                'n', 'N' -> b.append('\n')
                ',' -> b.append(',')
                ';' -> b.append(';')
                '\\' -> b.append('\\')
                else -> {
                    b.append(c); b.append(s[i + 1])
                }
            }
            i += 2
        } else {
            b.append(c); i++
        }
    }
    return b.toString()
}

fun unfoldIcalLines(text: String): List<String> {
    val out = mutableListOf<String>()
    for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        if (raw.isEmpty()) continue
        if ((raw.startsWith(' ') || raw.startsWith('\t')) && out.isNotEmpty()) {
            out[out.lastIndex] = out.last() + raw.substring(1)
        } else {
            out.add(raw)
        }
    }
    return out
}

internal data class ICalProp(val name: String, val params: Map<String, String>, val value: String)

private fun parseProp(line: String): ICalProp? {
    val ci = line.indexOf(':')
    if (ci < 0) return null
    val left = line.substring(0, ci)
    val value = line.substring(ci + 1)
    val segs = left.split(';')
    val name = segs.first().uppercase()
    val params = LinkedHashMap<String, String>()
    for (p in segs.drop(1)) {
        val eq = p.indexOf('=')
        if (eq < 0) continue
        params[p.substring(0, eq).uppercase()] = p.substring(eq + 1).trim('"')
    }
    return ICalProp(name, params, value)
}

private fun zoneOf(tzid: String?): ZoneId {
    if (tzid.isNullOrEmpty()) return ZoneId.systemDefault()
    return runCatching { ZoneId.of(tzid) }.getOrDefault(ZoneId.systemDefault())
}

/** Parse an ICS DATE-TIME/DATE value into a moment in the system zone. */
internal fun parseICalDateTime(prop: ICalProp): ICalMoment? {
    val v = prop.value.trim()
    if (v.isEmpty()) return null
    if (prop.params["VALUE"]?.uppercase() == "DATE" || Regex("^\\d{8}$").matches(v)) {
        val d = runCatching { LocalDate.parse(v, DATE_FMT) }.getOrNull() ?: return null
        return ICalMoment(d, true, null)
    }
    val utc = v.endsWith('Z')
    val core = if (utc) v.dropLast(1) else v
    if (!Regex("^\\d{8}T\\d{6}$").matches(core)) return null
    val zone = if (utc) ZoneId.of("UTC") else zoneOf(prop.params["TZID"])
    val local = runCatching { java.time.LocalDateTime.parse(core, DATETIME_FMT) }.getOrNull() ?: return null
    val zoned = local.atZone(zone).withZoneSameInstant(ZoneId.systemDefault())
    return ICalMoment(zoned.toLocalDate(), false, zoned.toLocalTime())
}

private val DURATION_RE = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

fun parseICalDuration(value: String): Duration? {
    val m = DURATION_RE.matchEntire(value.trim()) ?: return null
    if ((m.groupValues[1].isEmpty()) && m.groupValues.drop(2).all { it.isEmpty() }) return null
    var d = Duration.ZERO
    if (m.groupValues[1].isNotEmpty()) d = d.plusDays(m.groupValues[1].toLong())
    if (m.groupValues[2].isNotEmpty()) d = d.plusHours(m.groupValues[2].toLong())
    if (m.groupValues[3].isNotEmpty()) d = d.plusMinutes(m.groupValues[3].toLong())
    if (m.groupValues[4].isNotEmpty()) d = d.plusSeconds(m.groupValues[4].toLong())
    return d
}

private val DAY_MAP = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
    "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
    "SU" to DayOfWeek.SUNDAY,
)

fun parseICalRRule(value: String): ICalRecurrence? {
    val parts = value.split(';').mapNotNull {
        val eq = it.indexOf('=')
        if (eq < 0) null else it.substring(0, eq).uppercase() to it.substring(eq + 1)
    }.toMap()
    val freq = parts["FREQ"]?.uppercase() ?: return null
    if (freq != "DAILY" && freq != "WEEKLY") return null // MONTHLY/YEARLY ignored by design
    val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it > 0 }
    var until: LocalDate? = null
    parts["UNTIL"]?.let { u ->
        val t = u.trim()
        until = when {
            Regex("^\\d{8}$").matches(t) ->
                runCatching { LocalDate.parse(t, DATE_FMT) }.getOrNull()
            Regex("^\\d{8}T\\d{6}Z?$").matches(t) -> {
                val utc = t.endsWith('Z')
                val core = if (utc) t.dropLast(1) else t
                runCatching { java.time.LocalDateTime.parse(core, DATETIME_FMT) }.getOrNull()?.let { ldt ->
                    (if (utc) ldt.atZone(ZoneId.of("UTC")) else ldt.atZone(ZoneId.systemDefault()))
                        .withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                }
            }
            else -> null
        }
    }
    val byDay = parts["BYDAY"]?.split(',')
        ?.mapNotNull { DAY_MAP[it.trim().uppercase().takeLast(2)] }?.toSet() ?: emptySet()
    return ICalRecurrence(freq, interval, count, until, byDay)
}

fun parseICalendar(text: String): List<ICalEvent> {
    val lines = unfoldIcalLines(text)
    val events = mutableListOf<ICalEvent>()
    var inEvent = false
    var props = mutableListOf<ICalProp>()
    fun flush() {
        if (!inEvent) return
        inEvent = false
        buildICalEvent(props)?.let { events.add(it) }
        props = mutableListOf()
    }
    for (line in lines) {
        val prop = parseProp(line) ?: continue
        when {
            prop.name == "BEGIN" && prop.value.equals("VEVENT", true) -> {
                inEvent = true; props = mutableListOf()
            }
            prop.name == "END" && prop.value.equals("VEVENT", true) -> flush()
            inEvent -> props.add(prop)
        }
    }
    return events
}

private fun buildICalEvent(props: List<ICalProp>): ICalEvent? {
    if (props.any { it.name == "STATUS" && it.value.equals("CANCELLED", true) }) return null
    val startProp = props.firstOrNull { it.name == "DTSTART" } ?: return null
    val start = parseICalDateTime(startProp) ?: return null
    var end: ICalMoment? = props.firstOrNull { it.name == "DTEND" }?.let { parseICalDateTime(it) }
    if (end == null) {
        props.firstOrNull { it.name == "DURATION" }?.value?.let { dur ->
            parseICalDuration(dur)?.let { d ->
                if (!start.allDay) {
                    val zdt = start.date.atTime(start.time ?: LocalTime.MIDNIGHT)
                        .atZone(ZoneId.systemDefault()).plus(d)
                        .withZoneSameInstant(ZoneId.systemDefault())
                    end = ICalMoment(zdt.toLocalDate(), false, zdt.toLocalTime())
                } else if (!d.isZero && !d.isNegative) {
                    end = ICalMoment(start.date.plusDays(d.toDays()), true, null)
                }
            }
        }
    }
    if (end == null && !start.allDay) {
        end = ICalMoment(start.date, false, start.time)
    }
    val exDates = props.filter { it.name == "EXDATE" }.flatMap { ex ->
        ex.value.split(',').mapNotNull { parseICalDateTime(ICalProp("EXDATE", ex.params, it.trim()))?.date }
    }.toSet()
    val rrule = props.firstOrNull { it.name == "RRULE" }?.let { parseICalRRule(it.value) }
    val summary = props.firstOrNull { it.name == "SUMMARY" }?.value?.let { icalUnescape(it) }?.ifEmpty { null }
        ?: "(untitled)"
    return ICalEvent(
        uid = props.firstOrNull { it.name == "UID" }?.value?.trim().ifNullOrEmpty { "${summary.hashCode()}@${start.date}" },
        summary = summary,
        description = props.firstOrNull { it.name == "DESCRIPTION" }?.value?.let { icalUnescape(it) },
        location = props.firstOrNull { it.name == "LOCATION" }?.value?.let { icalUnescape(it) },
        start = start, end = end, exDates = exDates, recurrence = rrule,
    )
}

private fun String?.ifNullOrEmpty(fallback: () -> String): String {
    if (this == null || this.isEmpty()) return fallback()
    return this
}

/**
 * Expand events into day occurrences within [from]..[to] (inclusive).
 * Multi-day events yield one occurrence per spanned day.
 */
fun expandICalEvents(
    events: List<ICalEvent>,
    feedId: String,
    feedName: String,
    from: LocalDate,
    to: LocalDate,
): List<ICalOccurrence> {
    val out = mutableListOf<ICalOccurrence>()
    var budget = 4000
    fun emit(ev: ICalEvent, day: LocalDate, occStart: LocalDate, occEnd: LocalDate) {
        if (budget-- <= 0) return
        if (day < from || day > to || day in ev.exDates) return
        val singleDay = occStart == occEnd
        val startTime = if (ev.start.allDay) null
        else if (singleDay || day == occStart) ev.start.time else null
        val endTime = if (ev.end == null || ev.end.allDay) null
        else if (singleDay || day == occEnd) ev.end.time else null
        out.add(
            ICalOccurrence(
                uid = ev.uid, summary = ev.summary, description = ev.description,
                location = ev.location, date = day, allDay = ev.start.allDay,
                startTime = startTime, endTime = endTime, feedId = feedId, feedName = feedName,
            ),
        )
    }
    fun emitSpan(ev: ICalEvent, occStart: LocalDate) {
        // Span length comes from the event definition; the occurrence start
        // shifts it (DTEND is exclusive for DATE values).
        val rawEnd = ev.end?.date ?: if (ev.start.allDay) ev.start.date.plusDays(1) else ev.start.date
        val spanDays = if (ev.start.allDay) {
            (rawEnd.minusDays(1).toEpochDay() - ev.start.date.toEpochDay()).coerceAtLeast(0)
        } else {
            (rawEnd.toEpochDay() - ev.start.date.toEpochDay()).coerceAtLeast(0)
        }
        val occEnd = occStart.plusDays(spanDays)
        var d = occStart
        var guard = 0
        while (!d.isAfter(occEnd) && guard++ < 366) {
            emit(ev, d, occStart, occEnd)
            if (d == occEnd) break
            d = d.plusDays(1)
        }
    }
    for (ev in events) {
        val r = ev.recurrence
        if (r == null) {
            if (!ev.start.date.isAfter(to)) emitSpan(ev, ev.start.date)
            continue
        }
        // Occurrence-index iteration with fast-forward so ancient infinite
        // rules don't walk thousands of days.
        if (r.freq == "DAILY") {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(ev.start.date, from).toInt()
            var k = if (daysBetween > 0) daysBetween / r.interval else 0
            var guard = 0
            while (guard++ < 5000) {
                if (r.count != null && k >= r.count) break
                val day = ev.start.date.plusDays(k.toLong() * r.interval)
                if (day.isAfter(to)) break
                if (r.until != null && day.isAfter(r.until)) break
                if (!day.isBefore(from)) emitSpan(ev, day)
                k++
                if (budget <= 0) break
            }
        } else {
            val days = if (r.byDay.isNotEmpty()) r.byDay.sortedBy { it.value }
            else listOf(ev.start.date.dayOfWeek)
            val weekMonday = ev.start.date.with(DayOfWeek.MONDAY)
            val fromMonday = from.with(DayOfWeek.MONDAY)
            val weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(weekMonday, fromMonday).toInt()
            var w = if (weeksBetween > 0) (weeksBetween / r.interval) * r.interval else 0
            var produced = 0 // occurrences on/after ev.start (for COUNT)
            // Seed produced with occurrences strictly before the fast-forward point.
            // Cheaper: count them arithmetically is complex with BYDAY; instead
            // walk from 0 only when the span is small, else count weeks skipped.
            // Simplest correct: walk weeks from 0 but skip emitting before `from`.
            // Bound total iterations: window is small; ancient starts fast-forward.
            // To keep COUNT exact, pre-count skipped occurrences:
            if (w > 0) {
                var ww = 0
                while (ww < w) {
                    for (d in days) {
                        val day = weekMonday.plusDays(ww.toLong() * 7 + (d.value - 1).toLong())
                        if (!day.isBefore(ev.start.date)) produced++
                    }
                    ww += r.interval
                }
            }
            var guard = 0
            while (guard++ < 3000) {
                if (r.count != null && produced >= r.count) break
                val base = weekMonday.plusDays(w.toLong() * 7)
                if (base.isAfter(to) && base.isAfter(r.until ?: to)) break
                for (d in days) {
                    val day = base.plusDays((d.value - 1).toLong())
                    if (day.isBefore(ev.start.date)) continue
                    if (day.isAfter(to)) continue
                    if (r.until != null && day.isAfter(r.until)) continue
                    if (r.count != null && produced >= r.count) break
                    produced++
                    if (!day.isBefore(from)) emitSpan(ev, day)
                    if (budget <= 0) break
                }
                if (budget <= 0) break
                w += r.interval
                if (weekMonday.plusDays(w.toLong() * 7).isAfter(to)) {
                    // One more pass may still hold days <= to; the loop head handles it.
                    if (weekMonday.plusDays(w.toLong() * 7).isAfter(to.plusWeeks(1))) break
                }
            }
        }
    }
    return out.sortedWith(compareBy({ it.date }, { it.allDay }, { it.startTime }, { it.summary }))
}
