package com.astroboii47.commander

import java.security.MessageDigest
import java.time.LocalDate

/** PowerSchool guardian-portal protocol ported from the desktop
 *  BeepBeep-Mobile-adjacent `Powerschool` project
 *  (form login at /public/, server-rendered /guardian/home.html,
 *  per-course /guardian/scores.html metadata, read-only
 *  POST /ws/xte/assignment/lookup). Read-only: nothing here ever
 *  mutates portal state. Pure Kotlin, no Android dependencies, so it
 *  is fully covered by JVM unit tests. */

const val PS_DEFAULT_BASE_URL = "https://pschool.gcs.k12.in.us"

sealed class PsException(val code: String, message: String) : Exception(message) {
    class InvalidCredentials(msg: String = "PowerSchool rejected the username or password.") :
        PsException("INVALID_CREDENTIALS", msg)

    class SessionExpired(msg: String = "PowerSchool session expired. Sign in again.") :
        PsException("SESSION_EXPIRED", msg)

    class MfaRequired(msg: String = "PowerSchool is asking for a one-time verification code.") :
        PsException("MFA_REQUIRED", msg)

    class SsoRequired(msg: String = "PowerSchool redirected to an external identity provider.") :
        PsException("SSO_REQUIRED", msg)

    class Parse(msg: String) : PsException("PARSE_ERROR", msg)
    class Network(msg: String, val status: Int? = null) : PsException("NETWORK_ERROR", msg)
}

// ---------- Minimal JSON (XTE responses + on-device cache; no new deps) ----------

sealed interface JVal {
    data object Null : JVal
    data class Bool(val v: Boolean) : JVal
    data class Num(val v: Double) : JVal
    data class Str(val s: String) : JVal
    data class Arr(val l: List<JVal>) : JVal
    data class Obj(val m: Map<String, JVal>) : JVal
}

fun JVal.obj(key: String): JVal? = (this as? JVal.Obj)?.m?.get(key)
fun JVal.str(): String? = (this as? JVal.Str)?.s
fun JVal.bool(): Boolean? = (this as? JVal.Bool)?.v
fun JVal.num(): Double? = (this as? JVal.Num)?.v
fun JVal.arr(): List<JVal>? = (this as? JVal.Arr)?.l
fun JVal.objStr(key: String): String? = obj(key)?.str()
fun JVal.objNum(key: String): Double? = obj(key)?.num()
fun JVal.objBool(key: String): Boolean? = obj(key)?.bool()
fun JVal.objArr(key: String): List<JVal>? = obj(key)?.arr()

private class JsonReader(private val s: String) {
    var i = 0
    fun parse(): JVal {
        ws(); val v = value(); ws()
        if (i != s.length) throw PsException.Parse("Trailing characters in JSON.")
        return v
    }

    private fun ws() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun value(): JVal {
        if (i >= s.length) throw PsException.Parse("Truncated JSON.")
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> JVal.Str(str())
            't' -> lit("true", JVal.Bool(true))
            'f' -> lit("false", JVal.Bool(false))
            'n' -> lit("null", JVal.Null)
            '-', in '0'..'9' -> num()
            else -> throw PsException.Parse("Unexpected character '$c' in JSON.")
        }
    }

    private fun lit(word: String, v: JVal): JVal {
        if (!s.startsWith(word, i)) throw PsException.Parse("Bad literal in JSON.")
        i += word.length
        return v
    }

    private fun num(): JVal {
        val start = i
        if (s[i] == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
        return JVal.Num(s.substring(start, i).toDoubleOrNull()
            ?: throw PsException.Parse("Bad number in JSON."))
    }

    private fun str(): String {
        i++ // opening quote
        val b = StringBuilder()
        while (true) {
            if (i >= s.length) throw PsException.Parse("Unterminated string in JSON.")
            val c = s[i++]
            if (c == '"') break
            if (c != '\\') {
                b.append(c)
                continue
            }
            if (i >= s.length) throw PsException.Parse("Bad escape in JSON.")
            when (val e = s[i++]) {
                '"', '\\', '/' -> b.append(e)
                'b' -> b.append('\b')
                'f' -> b.append('\u000C')
                'n' -> b.append('\n')
                'r' -> b.append('\r')
                't' -> b.append('\t')
                'u' -> {
                    if (i + 4 > s.length) throw PsException.Parse("Bad unicode escape in JSON.")
                    b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar()
                        ?: throw PsException.Parse("Bad unicode escape in JSON."))
                    i += 4
                }
                else -> throw PsException.Parse("Bad escape in JSON.")
            }
        }
        return b.toString()
    }

    private fun arr(): JVal {
        i++; val out = mutableListOf<JVal>(); ws()
        if (i < s.length && s[i] == ']') {
            i++; return JVal.Arr(out)
        }
        while (true) {
            ws(); out.add(value()); ws()
            if (i >= s.length) throw PsException.Parse("Truncated array in JSON.")
            val c = s[i++]
            if (c == ']') break
            if (c != ',') throw PsException.Parse("Expected ',' in JSON array.")
        }
        return JVal.Arr(out)
    }

    private fun obj(): JVal {
        i++; val out = LinkedHashMap<String, JVal>(); ws()
        if (i < s.length && s[i] == '}') {
            i++; return JVal.Obj(out)
        }
        while (true) {
            ws()
            if (i >= s.length || s[i] != '"') throw PsException.Parse("Expected key in JSON object.")
            val k = str(); ws()
            if (i >= s.length || s[i] != ':') throw PsException.Parse("Expected ':' in JSON object.")
            i++; ws(); out[k] = value(); ws()
            if (i >= s.length) throw PsException.Parse("Truncated object in JSON.")
            val c = s[i++]
            if (c == '}') break
            if (c != ',') throw PsException.Parse("Expected ',' in JSON object.")
        }
        return JVal.Obj(out)
    }
}

fun parseJson(text: String): JVal = JsonReader(text).parse()

fun escapeJson(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}

// ---------- Text helpers (mirror the desktop collector) ----------

fun psClean(text: String?): String =
    (text ?: "").replace('\u00A0', ' ').replace(Regex("\\s+"), " ").trim()

fun psStripTags(html: String): String = psClean(html.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ", true))

fun psTrimNum(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    val n = value.toDoubleOrNull() ?: return ""
    if (!n.isFinite()) return ""
    // Match JS trimNum: Number(n.toFixed(4)) drops trailing zeros.
    val fixed = "%.4f".format(n)
    return fixed.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

/** MM/DD/YYYY -> "YYYY-M-D" (XTE wants unpadded, like the desktop client). */
fun mdyToXteDate(mdy: String?): String? {
    val m = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(mdy?.trim() ?: "") ?: return null
    return "${m.groupValues[3]}-${m.groupValues[1].toInt()}-${m.groupValues[2].toInt()}"
}

fun isoToMdy(iso: String?): String? {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(iso ?: "") ?: return null
    return "${m.groupValues[2]}/${m.groupValues[3]}/${m.groupValues[1]}"
}

/** "YYYY-M-D" -> "YYYY-MM-DD". */
fun isoFromXte(xte: String?): String? {
    val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(xte ?: "") ?: return null
    return "${m.groupValues[1]}-${m.groupValues[2].padStart(2, '0')}-${m.groupValues[3].padStart(2, '0')}"
}

/** "MM/DD/YYYY" -> LocalDate, strict. */
fun psMdyToLocalDate(mdy: String?): LocalDate? {
    val m = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(mdy?.trim() ?: "") ?: return null
    return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
}

/** "YYYY-MM-DD" -> LocalDate, strict. */
fun psIsoToLocalDate(iso: String?): LocalDate? {
    if (iso == null || !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(iso)) return null
    return runCatching { LocalDate.parse(iso) }.getOrNull()
}

fun psNumberValue(raw: String?): Double? {
    if (raw == null) return null
    val t = raw.trim()
    if (t.isEmpty() || t == "--" || Regex("^\\s*--\\s*/").containsMatchIn(t)) return null
    return Regex("^-?\\d+(?:\\.\\d+)?").find(t.replace(",", ""))?.value?.toDoubleOrNull()
}

/** "100/100" -> earned/possible. */
fun psPoints(raw: String?): Pair<Double?, Double?> {
    val m = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*/\\s*(-?\\d+(?:\\.\\d+)?)\\s*$")
        .matchEntire((raw ?: "").replace(",", "")) ?: return Pair(psNumberValue(raw), null)
    return Pair(m.groupValues[1].toDoubleOrNull(), m.groupValues[2].toDoubleOrNull())
}

fun sha256Hex24(input: String): String {
    val d = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return d.joinToString("") { "%02x".format(it) }.take(24)
}

// ---------- Login-page detection ----------

private val MFA_MARKERS = listOf(
    "multi-factor", "multifactor", "verification code", "authenticator",
    "duo ", "duo-", "one-time pass", "enter the code",
)

fun psIsLoginHtml(html: String?): Boolean {
    if (html.isNullOrEmpty()) return true
    if (html.contains("switchStudent(")) return false
    if (html.contains("Parent, Guardian, and Student Login")) return true
    val lower = html.lowercase()
    if (!lower.contains("loginform") && !lower.contains("password")) return false
    val hasForm = Regex("<form\\b[^>]*\\bid\\s*=\\s*\"?LoginForm", RegexOption.IGNORE_CASE).containsMatchIn(html)
    val hasPw = Regex("<input\\b[^>]*\\bid\\s*=\\s*\"?fieldPassword", RegexOption.IGNORE_CASE).containsMatchIn(html)
    val hasPwName = Regex("<input\\b[^>]*type\\s*=\\s*\"?password[^>]*name\\s*=\\s*\"?pw", RegexOption.IGNORE_CASE).containsMatchIn(html)
    return hasForm || hasPw || hasPwName
}

fun psHasMfaChallenge(html: String): Boolean {
    val lower = html.lowercase()
    return MFA_MARKERS.any { lower.contains(it) }
}

fun psLoginRejected(html: String): Boolean {
    if (Regex("class\\s*=\\s*\"[^\"]*(feedback-alert|error)[^\"]*\"|id\\s*=\\s*\"error\"", RegexOption.IGNORE_CASE).containsMatchIn(html)) return true
    return Regex("invalid (username|password|credentials)|sign in failed|login failed", RegexOption.IGNORE_CASE).containsMatchIn(html)
}

// ---------- Login form ----------

data class PsLoginForm(val action: String, val fields: Map<String, String>)

private val INPUT_TAG = Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ATTR = { html: String, name: String ->
    Regex(name + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE)
        .find(html)?.let { it.groupValues[1].ifEmpty { it.groupValues[2].ifEmpty { it.groupValues[3] } } }
}

fun parseLoginForm(html: String): PsLoginForm {
    val forms = Regex("<form\\b[^>]*>(.*?)</form>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(html).map { it }.toList()
    if (forms.isEmpty()) throw PsException.Parse("Sign-in form not found on the PowerSchool sign-in page.")
    val picked = forms.firstOrNull {
        Regex("\\bid\\s*=\\s*\"?LoginForm", RegexOption.IGNORE_CASE).containsMatchIn(it.value)
    } ?: forms.first()
    val fields = LinkedHashMap<String, String>()
    for (m in INPUT_TAG.findAll(picked.groupValues[1])) {
        val name = ATTR(m.value, "name") ?: continue
        fields[name] = ATTR(m.value, "value") ?: ""
    }
    val action = ATTR(picked.value.substringBefore('>'), "action")?.ifEmpty { null } ?: "/guardian/home.html"
    return PsLoginForm(action, fields)
}

/** Replicates the portal's doPCASLogin submit handler. */
fun buildLoginPayload(form: Map<String, String>, account: String, password: String): Map<String, String> {
    val out = form.toMutableMap()
    if (out.containsKey("pw") || !out.containsKey("password")) out["pw"] = password else out["password"] = password
    out["account"] = account
    if (out.containsKey("dbpw")) out["dbpw"] = password
    if (out.containsKey("ldappassword")) out["ldappassword"] = password
    val translator = out["translatorpw"] ?: ""
    val semi = translator.indexOf(';')
    if (semi < 0) {
        if (out.containsKey("translator_username")) out["translator_username"] = translator
        if (out.containsKey("translator_password")) out["translator_password"] = ""
    } else {
        if (out.containsKey("translator_username")) out["translator_username"] = translator.substring(0, semi)
        val rest = translator.substring(semi + 1)
        if (out.containsKey("translator_password")) out["translator_password"] = rest
        if (out.containsKey("translator_ldappassword")) out["translator_ldappassword"] = rest
    }
    return out
}

// ---------- Guardian home page ----------

data class PsScoreEntry(
    val courseId: String,
    val courseName: String,
    val teacher: String?,
    val expression: String?,
    val termCode: String,
    val scoresUrl: String,
    val gradeText: String?,
    val beginDate: String?,
    val endDate: String?,
    val schoolId: String?,
)

private val TABLE_RE = Regex("<table\\b[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val ROW_RE = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val CELL_RE = Regex("<t[dh]\\b[^>]*>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val LINK_RE = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun queryParam(href: String, name: String): String? {
    val q = href.substringAfter('?', "")
    if (q.isEmpty()) return null
    for (part in q.split('&')) {
        val k = part.substringBefore('=')
        if (k.equals(name, ignoreCase = false)) {
            val v = part.substringAfter('=', "")
            return runCatching { java.net.URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
        }
    }
    return null
}

private fun normalizeScoresUrl(href: String): String {
    val h = href.trim()
    if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(h) || h.startsWith('/')) return h
    return "/guardian/" + h.removePrefix("./")
}

private val GRADE_LETTER_NUM = Regex("^([A-DF][+-]?|P|S|U|I|NG)\\s+\\d+(?:\\.\\d+)?$", RegexOption.IGNORE_CASE)
private val I_BRACKET = Regex("\\[ ?i ?\\]", RegexOption.IGNORE_CASE)

fun normalizeGradeText(text: String): String? {
    if (I_BRACKET.containsMatchIn(text) || text.isEmpty()) return null
    if (GRADE_LETTER_NUM.matches(text)) return "$text%"
    return text
}

fun parseHomePage(html: String): List<PsScoreEntry> {
    val entries = mutableListOf<PsScoreEntry>()
    for (table in TABLE_RE.findAll(html)) {
        for (row in ROW_RE.findAll(table.groupValues[1])) {
            val rowHtml = row.groupValues[1]
            val cells = CELL_RE.findAll(rowHtml).map { it.groupValues[1] }.toList()
            if (cells.isEmpty()) continue
            data class L(val href: String, val text: String)
            val links = LINK_RE.findAll(rowHtml).map {
                val attrs = it.groupValues[1]
                val body = it.groupValues[2]
                val href = ATTR(attrs, "href") ?: ""
                L(href, psClean(psStripTags(body)))
            }.filter { it.href.contains("scores.html") }.toList()
            if (links.isEmpty()) continue
            var courseHref: String? = null
            val termLinks = mutableListOf<Triple<String, String, String>>() // fg, href, text
            for (a in links) {
                val fg = queryParam(a.href, "fg")
                if (fg != null) termLinks.add(Triple(fg.uppercase(), a.href, a.text))
                else if (courseHref == null) courseHref = a.href
            }
            if (termLinks.isEmpty()) continue
            val frn = queryParam(courseHref ?: termLinks.first().second, "frn") ?: continue
            var nameCell: String? = null
            if (courseHref != null) {
                val target = courseHref
                nameCell = cells.firstOrNull { cell ->
                    LINK_RE.findAll(cell).any { (ATTR(it.groupValues[1], "href") ?: "") == target }
                }
            }
            val chosen = nameCell ?: cells.maxByOrNull { psStripTags(it).length } ?: continue
            val firstChunk = chosen.split(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)).firstOrNull() ?: ""
            var courseName = psClean(psStripTags(firstChunk).replace("&nbsp;", " ", true))
            if (courseName.isEmpty()) courseName = psStripTags(chosen)
            var teacher = ""
            val teacherLink = LINK_RE.findAll(rowHtml).firstOrNull {
                val href = ATTR(it.groupValues[1], "href") ?: ""
                href.contains("teacherinfo.html") || href.startsWith("mailto:")
            }
            if (teacherLink != null) {
                teacher = psClean(psStripTags(teacherLink.groupValues[2])).removePrefix("Email ").removePrefix("email ")
            }
            val expression = psClean(psStripTags(cells.firstOrNull() ?: ""))
            for ((fg, href, text) in termLinks) {
                entries.add(
                    PsScoreEntry(
                        courseId = frn,
                        courseName = courseName.ifEmpty { "Unknown course" },
                        teacher = teacher.ifEmpty { null },
                        expression = expression.ifEmpty { null },
                        termCode = fg,
                        scoresUrl = normalizeScoresUrl(href),
                        gradeText = normalizeGradeText(text),
                        beginDate = queryParam(href, "begdate"),
                        endDate = queryParam(href, "enddate"),
                        schoolId = queryParam(href, "schoolid"),
                    ),
                )
            }
        }
    }
    val seen = HashSet<String>()
    return entries.filter { e ->
        val k = "${e.courseId}|${e.termCode}"
        if (!seen.add(k)) false else true
    }
}

// ---------- Scores-page metadata + XTE lookup mapping ----------

fun parseScoresMetadata(html: String): Pair<String, String> {
    val section = Regex("data-sectionid=\"(\\d+)\"").find(html)?.groupValues?.get(1)
        ?: throw PsException.Parse("Section identifier not found on the class score page.")
    val frn = Regex("studentFRN\\s*=\\s*'(\\d+)'").find(html)?.groupValues?.get(1)
        ?: throw PsException.Parse("Student reference not found on the class score page.")
    val studentApiId = frn.removePrefix("001")
    if (!section.all { it.isDigit() } || !studentApiId.all { it.isDigit() }) {
        throw PsException.Parse("Class score identifiers were not numeric.")
    }
    return Pair(section.trimStart('0').ifEmpty { "0" }, studentApiId.trimStart('0').ifEmpty { "0" })
}

data class RawAssignment(
    val assignmentId: String?,
    val dueDate: String?, // MM/DD/YYYY or null
    val category: String?,
    val name: String?,
    val flags: List<String>,
    val score: String,
    val percent: String,
    val grade: String,
)

data class RawRecord(
    val course: String,
    val teacher: String?,
    val expression: String?,
    val termCode: String,
    val courseId: String,
    val gradeText: String?,
    val beginDate: String?,
    val endDate: String?,
    val schoolId: String?,
    val assignments: List<RawAssignment>,
)

fun mapLookupToAssignments(
    lookup: JVal,
    courseId: String,
    begIso: String?,
    endIso: String?,
): List<RawAssignment> {
    val items = lookup.arr() ?: throw PsException.Parse("Assignment lookup did not return a list.")
    val out = mutableListOf<RawAssignment>()
    for (item in items) {
        val sections = item.objArr("_assignmentsections") ?: continue
        for (section in sections) {
            val dueRaw = section.objStr("duedate")
            val dueIso = if (!dueRaw.isNullOrEmpty()) dueRaw.take(10) else null
            if (dueIso != null) {
                if (begIso != null && dueIso < begIso) continue
                if (endIso != null && dueIso > endIso) continue
            }
            var category = ""
            for (assoc in section.objArr("_assignmentcategoryassociations") ?: emptyList()) {
                if (assoc.objBool("isprimary") == true) {
                    val n = assoc.obj("_teachercategory")?.objStr("name")
                    if (!n.isNullOrEmpty()) {
                        category = psClean(n); break
                    }
                }
            }
            val score = section.objArr("_assignmentscores")?.firstOrNull()
            val flags = mutableListOf<String>()
            var scoreText = ""; var percentText = ""; var gradeText = ""
            if (score != null) {
                when {
                    score.objBool("isexempt") == true -> flags.add("exempt")
                    score.objBool("ismissing") == true -> flags.add("missing")
                    score.objBool("isincomplete") == true -> flags.add("incomplete")
                    score.objBool("isabsent") == true -> flags.add("absent")
                    else -> {
                        if (score.objBool("islate") == true) flags.add("late")
                        if (score.objBool("iscollected") == true) flags.add("collected")
                        val hasPoints = (score.objNum("scorepoints") ?: 0.0) > 0
                        val hasEntered = !(score.objStr("actualscoreentered") ?: "").trim().isEmpty()
                        val hasPercent = (score.objNum("scorepercent") ?: 0.0) > 0
                        if (hasPoints || hasEntered || hasPercent) {
                            // scoreentrypoints lives on the section, not the score row.
                            val entryPts = psTrimNum(numToStr(section, "scoreentrypoints"))
                            scoreText = "${psTrimNum(numToStr(score, "scorepoints"))}/$entryPts"
                            val pct = score.obj("scorepercent")
                            percentText = if (pct == null || pct is JVal.Null) "" else psTrimNum(
                                (pct as? JVal.Num)?.v?.toString() ?: pct.str() ?: "",
                            )
                            gradeText = score.objStr("scorelettergrade") ?: ""
                        }
                    }
                }
            }
            if (section.objBool("iscountedinfinalgrade") == false) flags.add("excluded from final grade")
            out.add(
                RawAssignment(
                    assignmentId = item.obj("assignmentid")?.let { (it as? JVal.Num)?.v?.toLong()?.toString() ?: it.str() },
                    dueDate = dueIso?.let { isoToMdy(it) },
                    category = category.ifEmpty { null },
                    name = section.objStr("name")?.let { psClean(it).ifEmpty { null } },
                    flags = flags,
                    score = scoreText,
                    percent = percentText,
                    grade = gradeText,
                ),
            )
        }
    }
    return out
}

private fun numToStr(v: JVal, key: String): String {
    val o = v.obj(key) ?: return ""
    return when (o) {
        is JVal.Num -> {
            val l = o.v.toLong()
            if (o.v == l.toDouble()) l.toString() else o.v.toString()
        }
        is JVal.Str -> o.s
        else -> ""
    }
}

// ---------- Normalized store model (mirrors desktop normalize()) ----------

data class PsAssignment(
    val id: String,
    val courseId: String,
    val courseName: String,
    val term: String,
    val dueIso: String?, // YYYY-MM-DD
    val dueMdy: String?,
    val category: String?,
    val name: String?,
    val earned: Double?,
    val possible: Double?,
    val percent: Double?,
    val letter: String?,
    val status: String, // graded|missing|late|exempt|ungraded|future|submitted|incomplete|absent
    val flags: List<String>,
)

data class PsCourse(
    val id: String,
    val name: String,
    val teacher: String?,
    val expression: String?,
    val term: String,
    val gradeLetter: String?,
    val gradePercent: Double?,
    val gradeStatus: String, // available|unavailable
    val gradeRaw: String?,
    val assignments: List<PsAssignment>,
)

private val GRADE_LETTER_RE = Regex("^(A\\+?|B\\+?|C\\+?|D\\+?|F|P|S|U|I|NG)\\b", RegexOption.IGNORE_CASE)
private val PERCENT_RE = Regex("-?\\d+(?:\\.\\d+)?\\s*%")

fun normalizeRawRecords(records: List<RawRecord>, today: LocalDate = LocalDate.now()): List<PsCourse> {
    val courses = LinkedHashMap<String, MutableList<RawRecord>>()
    // Normalize keyed by courseId:termCode (desktop uses termCode as term).
    data class NrmAssignment(val a: PsAssignment, val key: String)
    val byKey = LinkedHashMap<String, PsCourseBuilder>()
    for (r in records) {
        val cid = r.courseId
        if (cid.isEmpty()) continue
        val term = r.termCode.uppercase()
        val key = "$cid:$term"
        val b = byKey.getOrPut(key) {
            val gradeText = (r.gradeText ?: "").trim()
            val blank = gradeText.isEmpty() || gradeText == "--" || Regex("^-+$").matches(gradeText)
            val letter = if (blank) null else GRADE_LETTER_RE.find(gradeText)?.groupValues?.get(1)?.uppercase()
                ?: (if (gradeText.isNotEmpty() && !gradeText.contains('%')) gradeText else null)
            val pctRaw = PERCENT_RE.find(gradeText)?.value
            PsCourseBuilder(
                id = cid, name = r.course.ifEmpty { "Unknown course" }, teacher = r.teacher,
                expression = r.expression, term = term, gradeLetter = letter,
                gradePercent = psNumberValue(pctRaw), gradeStatus = if (!blank) "available" else "unavailable",
                gradeRaw = r.gradeText,
            )
        }
        for (a in r.assignments) {
            val dueIso = a.dueDate?.let { mdy ->
                val m = Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(mdy) ?: return@let null
                val iso = "${m.groupValues[3]}-${m.groupValues[1].padStart(2, '0')}-${m.groupValues[2].padStart(2, '0')}"
                if (psIsoToLocalDate(iso) == null) null else iso
            }
            val flags = a.flags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            fun has(s: String) = flags.any { it.contains(s, ignoreCase = true) }
            val missing = has("missing"); val exempt = has("exempt"); val late = has("late")
            val collected = has("collected"); val absent = has("absent"); val incomplete = has("incomplete")
            val due = dueIso?.let { psIsoToLocalDate(it) }
            val future = due != null && due.isAfter(today)
            val ungraded = !missing && !exempt && !absent && !incomplete && psNumberValue(a.score) == null
            val status = when {
                exempt -> "exempt"
                missing -> "missing"
                incomplete -> "incomplete"
                absent -> "absent"
                future && ungraded -> "future"
                ungraded -> "ungraded"
                late -> "late"
                collected -> "submitted"
                else -> "graded"
            }
            val stable = if (!a.assignmentId.isNullOrEmpty()) a.assignmentId else sha256Hex24(
                "$cid|${dueIso ?: "no-date"}|${(a.name ?: "").lowercase()}|${(a.category ?: "").lowercase()}",
            )
            val (earned, possible) = psPoints(a.score)
            b.assignments.add(
                PsAssignment(
                    id = stable, courseId = cid, courseName = b.name, term = term,
                    dueIso = dueIso, dueMdy = a.dueDate, category = a.category, name = a.name,
                    earned = earned, possible = possible, percent = psNumberValue(a.percent),
                    letter = a.grade.ifEmpty { null }, status = status, flags = flags.toList(),
                ),
            )
        }
        courses.getOrPut(key) { mutableListOf() }.add(r)
    }
    return byKey.values.map { b ->
        val seen = HashSet<String>()
        val deduped = b.assignments.filter { a ->
            seen.add("${a.dueIso}|${a.name}|${a.category}")
        }
        PsCourse(
            id = b.id, name = b.name, teacher = b.teacher, expression = b.expression,
            term = b.term, gradeLetter = b.gradeLetter, gradePercent = b.gradePercent,
            gradeStatus = b.gradeStatus, gradeRaw = b.gradeRaw, assignments = deduped,
        )
    }.sortedWith(compareBy({ it.name.lowercase() }, { termOrder(it.term) }))
}

private class PsCourseBuilder(
    val id: String, val name: String, val teacher: String?, val expression: String?,
    val term: String, val gradeLetter: String?, val gradePercent: Double?,
    val gradeStatus: String, val gradeRaw: String?,
    val assignments: MutableList<PsAssignment> = mutableListOf(),
)

/** 4.0-scale points for a percent, standard 10-point band. */
fun psPointsForPercent(percent: Double?): Double? {
    if (percent == null) return null
    return when {
        percent >= 90.0 -> 4.0
        percent >= 80.0 -> 3.0
        percent >= 70.0 -> 2.0
        percent >= 60.0 -> 1.0
        else -> 0.0
    }
}

/** Unweighted GPA over courses that carry a percent. Null when none do. */
fun psCourseGpa(courses: List<PsCourse>): Double? {
    val pts = courses.mapNotNull { psPointsForPercent(it.gradePercent) }
    if (pts.isEmpty()) return null
    return pts.average()
}

/** Count of courses by leading letter (A/B/C/D/F), in that order. */
fun psLetterCounts(courses: List<PsCourse>): List<Pair<String, Int>> {
    val counts = linkedMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0, "F" to 0)
    for (c in courses) {
        val letter = c.gradeLetter?.uppercase()?.firstOrNull()
        val key = when (letter) {
            'A' -> "A"; 'B' -> "B"; 'C' -> "C"; 'D' -> "D"; 'F' -> "F"
            else -> c.gradePercent?.let { psPointsForPercent(it)?.let { p -> "ABCDF"[(4 - p.toInt()).coerceIn(0, 4)].toString() } }
        }
        if (key != null) counts[key] = (counts[key] ?: 0) + 1
    }
    return counts.filter { it.value > 0 }.toList()
}

data class PsGradeMove(val courseId: String, val courseName: String, val oldPercent: Double, val newPercent: Double) {
    val delta: Double get() = newPercent - oldPercent
}

/**
 * Biggest per-course percent moves between two snapshots, keyed by
 * courseId|term. Returns up to [limit] ordered by magnitude: drops first,
 * then gains — the shape the dashboard's School card expects.
 */
fun psGradeMovers(
    prev: Map<String, Double>,
    cur: List<PsCourse>,
    limit: Int = 2,
): List<PsGradeMove> {
    val moves = mutableListOf<PsGradeMove>()
    for (c in cur) {
        val now = c.gradePercent ?: continue
        val was = prev["${c.id}|${c.term}"] ?: continue
        val d = now - was
        if (kotlin.math.abs(d) >= 0.5) {
            moves.add(PsGradeMove(c.id, c.name, was, now))
        }
    }
    val drops = moves.filter { it.delta < 0 }.sortedBy { it.delta }
    val gains = moves.filter { it.delta >= 0 }.sortedByDescending { it.delta }
    return (drops + gains).take(limit)
}

/** Canonical term ordering for chips/cards. Unknown terms sort last. */
fun termOrder(term: String): Int = when (term.uppercase()) {
    "Q1" -> 0; "Q2" -> 1; "Q3" -> 2; "Q4" -> 3
    "S1" -> 4; "S2" -> 5
    else -> 99
}

fun psCourseTerms(courses: List<PsCourse>): List<String> =
    courses.map { it.term }.distinct().sortedBy { termOrder(it) }

// ---------- Cache JSON (compact; parsed by the mini parser) ----------

fun psCoursesToJson(courses: List<PsCourse>): String = buildString {
    append('[')
    courses.forEachIndexed { ci, c ->
        if (ci > 0) append(',')
        append("{\"id\":\"${escapeJson(c.id)}\",\"name\":\"${escapeJson(c.name)}\",")
        append("\"teacher\":${c.teacher?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        append("\"expression\":${c.expression?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        append("\"term\":\"${escapeJson(c.term)}\",")
        append("\"gradeLetter\":${c.gradeLetter?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        append("\"gradePercent\":${c.gradePercent?.toString() ?: "null"},")
        append("\"gradeStatus\":\"${escapeJson(c.gradeStatus)}\",")
        append("\"gradeRaw\":${c.gradeRaw?.let { "\"${escapeJson(it)}\"" } ?: "null"},\"assignments\":[")
        c.assignments.forEachIndexed { ai, a ->
            if (ai > 0) append(',')
            append("{\"id\":\"${escapeJson(a.id)}\",\"dueIso\":${a.dueIso?.let { "\"$it\"" } ?: "null"},")
            append("\"dueMdy\":${a.dueMdy?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"category\":${a.category?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"name\":${a.name?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"earned\":${a.earned?.toString() ?: "null"},\"possible\":${a.possible?.toString() ?: "null"},")
            append("\"percent\":${a.percent?.toString() ?: "null"},")
            append("\"letter\":${a.letter?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
            append("\"status\":\"${escapeJson(a.status)}\",\"flags\":[")
            a.flags.forEachIndexed { fi, f ->
                if (fi > 0) append(',')
                append("\"${escapeJson(f)}\"")
            }
            append("]}")
        }
        append("]}")
    }
    append(']')
}

fun psCoursesFromJson(text: String): List<PsCourse> {
    val root = parseJson(text)
    val arr = root.arr() ?: throw PsException.Parse("PowerSchool cache is malformed.")
    return arr.mapNotNull { c ->
        if (c !is JVal.Obj) return@mapNotNull null
        val id = c.objStr("id") ?: return@mapNotNull null
        val name = c.objStr("name") ?: "Unknown course"
        val term = c.objStr("term") ?: "UNKNOWN"
        PsCourse(
            id = id, name = name, teacher = c.objStr("teacher"), expression = c.objStr("expression"),
            term = term, gradeLetter = c.objStr("gradeLetter"), gradePercent = c.obj("gradePercent")?.num(),
            gradeStatus = c.objStr("gradeStatus") ?: "unavailable", gradeRaw = c.objStr("gradeRaw"),
            assignments = (c.objArr("assignments") ?: emptyList()).mapNotNull { a ->
                if (a !is JVal.Obj) return@mapNotNull null
                PsAssignment(
                    id = a.objStr("id") ?: return@mapNotNull null, courseId = id, courseName = name,
                    term = term, dueIso = a.objStr("dueIso"), dueMdy = a.objStr("dueMdy"),
                    category = a.objStr("category"), name = a.objStr("name"),
                    earned = a.obj("earned")?.num(), possible = a.obj("possible")?.num(),
                    percent = a.obj("percent")?.num(), letter = a.objStr("letter"),
                    status = a.objStr("status") ?: "ungraded",
                    flags = (a.objArr("flags") ?: emptyList()).mapNotNull { it.str() },
                )
            },
        )
    }
}
