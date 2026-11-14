package com.astroboii47.commander

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** HTTPS client for the PowerSchool guardian portal. Mirrors the desktop
 *  `pshttp.js`/`collector.js`: manual redirects, portal-only cookie jar,
 *  session persisted to internal storage (cookies only, never the password).
 *  All calls are suspending and run on Dispatchers.IO. */

private const val SESSION_TTL_FALLBACK_MS = 4 * 60 * 60 * 1000L
private const val PS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

data class PsCookie(
    val name: String,
    val value: String,
    var domain: String,
    val path: String = "/",
    val expiresAt: Long? = null,
    val secure: Boolean = false,
)

class PowerSchoolClient(
    baseUrl: String,
    filesDir: File,
    var username: String? = null,
) {
    val baseUrl: String = baseUrl.trim().trimEnd('/')
        .also { require(it.startsWith("http://") || it.startsWith("https://")) { "PowerSchool base URL must be http(s)." } }
    val baseHost: String = URL(baseUrl).host
    private val baseHostname = URL(baseUrl).host.lowercase()
    private val dir = File(filesDir, "powerschool").apply { mkdirs() }
    private val sessionFile = File(dir, "session.json")
    private val cookies = LinkedHashMap<String, PsCookie>()
    private var expiresAt: Long? = null

    // ---------- cookies ----------

    private fun domainMatches(requestHost: String, cookieDomain: String): Boolean {
        val host = requestHost.lowercase()
        val dom = cookieDomain.lowercase().removePrefix(".")
        return host == dom || host.endsWith(".$dom")
    }

    private fun cookieHeader(url: URL): String {
        val isHttps = url.protocol == "https"
        val now = System.currentTimeMillis()
        val out = mutableListOf<String>()
        val it = cookies.entries.iterator()
        while (it.hasNext()) {
            val c = it.next().value
            if (c.expiresAt != null && c.expiresAt <= now) {
                it.remove(); continue
            }
            if (c.secure && !isHttps) continue
            if (!domainMatches(url.host, c.domain)) continue
            if (!url.path.startsWith(c.path)) continue
            out.add("${c.name}=${c.value}")
        }
        return out.joinToString("; ")
    }

    private fun storeCookies(headers: Map<String, List<String>>, url: URL) {
        val setCookies = headers.entries
            .firstOrNull { it.key?.equals("set-cookie", ignoreCase = true) == true }
            ?.value ?: return
        for (header in setCookies) {
            val c = parseSetCookie(header, url.host.lowercase()) ?: continue
            if (!domainMatches(baseHostname, c.domain) && !domainMatches(url.host.lowercase(), c.domain)) continue
            val fixed = c.copy(domain = if (c.domain == url.host.lowercase()) baseHostname else c.domain)
            if (fixed.value.isEmpty() && (fixed.expiresAt ?: Long.MAX_VALUE) <= System.currentTimeMillis()) {
                cookies.remove(fixed.name); continue
            }
            cookies[fixed.name] = fixed
        }
    }

    fun sessionValid(): Boolean {
        val exp = expiresAt ?: return false
        return cookies.isNotEmpty() && System.currentTimeMillis() < exp
    }

    fun hasSession(): Boolean = cookies.isNotEmpty()

    fun signOut() {
        cookies.clear(); expiresAt = null; username = null
        sessionFile.delete()
    }

    fun saveSession(): Boolean = runCatching {
        val b = StringBuilder()
        b.append("{\"version\":1,")
        b.append("\"baseUrl\":\"${escapeJson(baseUrl)}\",")
        b.append("\"username\":${username?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        b.append("\"expiresAt\":${expiresAt?.let { "\"${java.time.Instant.ofEpochMilli(it)}\"" } ?: "null"},")
        b.append("\"savedAt\":\"${escapeJson(java.time.Instant.now().toString())}\",\"cookies\":[")
        cookies.values.forEachIndexed { i, c ->
            if (i > 0) b.append(',')
            b.append("{\"name\":\"${escapeJson(c.name)}\",\"value\":\"${escapeJson(c.value)}\",")
            b.append("\"domain\":\"${escapeJson(c.domain)}\",\"path\":\"${escapeJson(c.path)}\",")
            b.append("\"expires\":${c.expiresAt?.toString() ?: "null"},\"secure\":${c.secure}}")
        }
        b.append("]}")
        val tmp = File(dir, "session.json.tmp")
        tmp.writeText(b.toString())
        if (!tmp.renameTo(sessionFile)) {
            sessionFile.delete()
            tmp.renameTo(sessionFile)
        }
        true
    }.getOrDefault(false)

    fun loadSession(): Boolean = runCatching {
        if (!sessionFile.exists()) return false
        val root = parseJson(sessionFile.readText())
        if (root !is JVal.Obj) return false
        if (root.objStr("baseUrl") != null && root.objStr("baseUrl") != baseUrl) return false
        val arr = root.objArr("cookies") ?: return false
        if (arr.isEmpty()) return false
        val expStr = root.objStr("expiresAt")
        if (expStr != null && runCatching { java.time.Instant.parse(expStr).toEpochMilli() }.getOrNull()
            ?.let { it <= System.currentTimeMillis() } == true
        ) return false
        cookies.clear()
        for (c in arr) {
            if (c !is JVal.Obj) continue
            val name = c.objStr("name") ?: continue
            cookies[name] = PsCookie(
                name = name, value = c.objStr("value") ?: "",
                domain = c.objStr("domain") ?: baseHostname, path = c.objStr("path") ?: "/",
                expiresAt = c.obj("expires")?.num()?.toLong(),
                secure = c.objBool("secure") == true,
            )
        }
        username = root.objStr("username")
        expiresAt = expStr?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: sessionExpiryFallback()
        if (!sessionValid()) {
            cookies.clear(); return false
        }
        true
    }.getOrDefault(false)

    private fun sessionExpiryFallback(): Long {
        val max = cookies.values.mapNotNull { it.expiresAt }.maxOrNull() ?: 0L
        return if (max > System.currentTimeMillis() + 30 * 60 * 1000) max
        else System.currentTimeMillis() + SESSION_TTL_FALLBACK_MS
    }

    // ---------- HTTP ----------

    private data class RawResponse(val status: Int, val headers: Map<String, List<String>>, val body: String, val url: String)

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) {
            try {
                conn.inputStream
            } catch (_: Exception) {
                null
            }
        } else {
            try {
                conn.errorStream
            } catch (_: Exception) {
                null
            }
        } ?: return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    private fun request(
        method: String,
        pathOrUrl: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String? = null,
    ): RawResponse {
        var urlStr = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl
        else baseUrl + (if (pathOrUrl.startsWith("/")) "" else "/") + pathOrUrl
        var currentMethod = method
        var currentBody = body
        repeat(6) {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20000
                readTimeout = 20000
                requestMethod = currentMethod
                setRequestProperty("User-Agent", UA)
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                for ((k, v) in headers) setRequestProperty(k, v)
                val jar = cookieHeader(url)
                if (jar.isNotEmpty()) setRequestProperty("Cookie", jar)
                if (currentBody != null) {
                    doOutput = true
                    outputStream.bufferedWriter(StandardCharsets.UTF_8).use { w -> w.write(currentBody) }
                }
            }
            val status: Int
            try {
                status = conn.responseCode
            } catch (e: Exception) {
                conn.disconnect()
                throw PsException.Network("PowerSchool network request failed: ${e.message?.take(120)}")
            }
            storeCookies(conn.headerFields.filterKeys { it != null } as Map<String, List<String>>, url)
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                val bodyText = readBody(conn)
                conn.disconnect()
                if (location.isNullOrEmpty()) throw PsException.Network("Redirect without location (HTTP $status).", status)
                val next = URL(url, location)
                if (next.path.startsWith("/public/")) {
                    throw PsException.SessionExpired("PowerSchool redirected to the signed-out area. Sign in again.")
                }
                if (next.host != baseHost && next.host != url.host) {
                    throw PsException.SsoRequired(
                        "PowerSchool redirected to an external identity provider (${next.host}).",
                    )
                }
                urlStr = next.toString()
                if (status == 303 || ((status == 301 || status == 302) && currentMethod == "POST")) {
                    currentMethod = "GET"; currentBody = null
                }
                return@repeat
            }
            val text = readBody(conn)
            conn.disconnect()
            return RawResponse(status, emptyMap(), text, urlStr)
        }
        throw PsException.Network("Too many redirects.")
    }

    private fun isPublicPath(path: String) = path.startsWith("/public/")

    suspend fun login(account: String, password: String): Unit = withContext(Dispatchers.IO) {
        if (account.isBlank() || account.any { it.isISOControl() }) {
            throw PsException.InvalidCredentials("Missing username.")
        }
        if (password.isEmpty() || password.any { it.isISOControl() }) {
            throw PsException.InvalidCredentials("Missing password.")
        }
        val formPage = try {
            request("GET", "/public/")
        } catch (e: PsException) {
            throw e
        } catch (e: Exception) {
            throw PsException.Network("Could not read the PowerSchool sign-in page.")
        }
        if (URL(formPage.url).host != baseHost) throw PsException.SsoRequired()
        val form = try {
            parseLoginForm(formPage.body)
        } catch (e: PsException) {
            throw e
        }
        val payload = buildLoginPayload(form.fields, account, password)
        val encoded = payload.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val resp = try {
            request(
                "POST", form.action, body = encoded,
                contentType = "application/x-www-form-urlencoded",
                headers = mapOf("Referer" to "$baseUrl/public/"),
            )
        } catch (e: PsException) {
            throw e
        } catch (e: Exception) {
            throw PsException.Network("PowerSchool sign-in request failed.")
        }
        if (URL(resp.url).host != baseHost) throw PsException.SsoRequired()
        if (psHasMfaChallenge(resp.body)) throw PsException.MfaRequired(
            "PowerSchool is asking for a one-time verification code, which this app cannot complete. " +
                "Sign in once on a computer instead.",
        )
        if (isPublicPath(URL(resp.url).path) || psIsLoginHtml(resp.body)) {
            if (psLoginRejected(resp.body)) throw PsException.InvalidCredentials()
            throw PsException.SessionExpired("Sign-in did not complete. Check the account, then try again.")
        }
        username = account
        expiresAt = sessionExpiryFallback()
        saveSession()
    }

    suspend fun getText(pathOrUrl: String): String = withContext(Dispatchers.IO) {
        val resp = try {
            request("GET", pathOrUrl)
        } catch (e: PsException) {
            throw e
        } catch (e: Exception) {
            throw PsException.Network("PowerSchool network request failed.")
        }
        if (resp.status == 401 || resp.status == 403) {
            throw PsException.SessionExpired("PowerSchool rejected the session. Sign in again.")
        }
        if (resp.status != 200) throw PsException.Network("Unexpected PowerSchool status ${resp.status}.", resp.status)
        if (isPublicPath(URL(resp.url).path) || psIsLoginHtml(resp.body)) {
            throw PsException.SessionExpired("PowerSchool returned its sign-in page. Sign in again.")
        }
        resp.body
    }

    private suspend fun postLookup(scoresUrl: String, sectionId: String, studentApiId: String, beginXte: String, endXte: String): JVal =
        withContext(Dispatchers.IO) {
            val payload = "{\"section_ids\":[${sectionId.toLongOrNull() ?: sectionId}]," +
                "\"student_ids\":[${studentApiId.toLongOrNull() ?: studentApiId}]," +
                "\"start_date\":\"$beginXte\",\"end_date\":\"$endXte\"}"
            val resp = try {
                request(
                    "POST", "/ws/xte/assignment/lookup?_=${System.currentTimeMillis()}",
                    body = payload, contentType = "application/json;charset=UTF-8",
                    headers = mapOf(
                        "Accept" to "application/json, text/plain, */*",
                        "Origin" to baseUrl,
                        "Referer" to baseUrl + (if (scoresUrl.startsWith("/")) "" else "/") + scoresUrl,
                    ),
                )
            } catch (e: PsException) {
                throw e
            } catch (e: Exception) {
                throw PsException.Network("Assignment lookup failed.")
            }
            if (resp.status == 401 || resp.status == 403) {
                throw PsException.SessionExpired("PowerSchool rejected the assignment lookup. Sign in again.")
            }
            if (resp.status != 200) throw PsException.Network("Assignment lookup failed (HTTP ${resp.status}).", resp.status)
            try {
                parseJson(resp.body)
            } catch (e: PsException) {
                if (psIsLoginHtml(resp.body)) throw PsException.SessionExpired("PowerSchool session expired during assignment lookup.")
                throw PsException.Parse("Assignment lookup did not return JSON.")
            }.also {
                if (it !is JVal.Arr) {
                    throw PsException.Parse("Assignment lookup did not return a list.")
                }
            }
        }

    private fun isRetryable(e: Exception): Boolean {
        val n = e as? PsException.Network ?: return false
        return n.status == null || n.status == 429 || (n.status ?: 0) >= 500
    }

    /**
     * Full refresh: home page, then one score page + lookup per course-term.
     * [account]/[password] are used only when no usable saved session exists;
     * the password is never written to disk.
     */
    suspend fun collectAll(
        account: String?,
        password: String?,
        status: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<RawRecord> = withContext(Dispatchers.IO) {
        if (!sessionValid()) {
            if (!loadSession() && !(account != null && password != null)) {
                throw PsException.SessionExpired("No saved PowerSchool session. Sign in first.")
            }
            if (!sessionValid()) {
                if (account == null || password == null) {
                    throw PsException.SessionExpired("Saved session expired. Sign in again.")
                }
                try {
                    login(account, password)
                } catch (e: PsException.SessionExpired) {
                    // Saved session was stale; fresh login with the same creds already attempted inside login().
                    throw e
                }
            }
        }
        val home = try {
            getText("/guardian/home.html")
        } catch (e: PsException.SessionExpired) {
            // One transparent re-login when a password is at hand.
            if (account != null && password != null) {
                login(account, password)
                getText("/guardian/home.html")
            } else throw e
        }
        val entries = parseHomePage(home)
        if (entries.isEmpty()) throw PsException.Parse("No class score links found on Grades and Attendance.")
        val done = AtomicInteger(0)
        val sem = Semaphore(3)
        coroutineScope {
            entries.map { entry ->
                async {
                    sem.withPermit {
                        var attempt = 0
                        while (true) {
                            try {
                                return@async collectEntry(entry)
                            } catch (e: PsException.SessionExpired) {
                                throw e
                            } catch (e: Exception) {
                                if (attempt >= 1 || !isRetryable(e)) throw e
                                attempt++
                                kotlinx.coroutines.delay(800)
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        throw PsException.Network("Unreachable.")
                    }.also { status(done.incrementAndGet(), entries.size) }
                }
            }.awaitAll()
        }
    }

    private suspend fun collectEntry(entry: PsScoreEntry): RawRecord {
        val scoresHtml = try {
            getText(entry.scoresUrl)
        } catch (e: PsException.SessionExpired) {
            throw PsException.SessionExpired("PowerSchool session expired during refresh. Sign in again, then retry.")
        }
        val (sectionId, studentApiId) = parseScoresMetadata(scoresHtml)
        val beginXte = mdyToXteDate(entry.beginDate) ?: mdyToXteDate("01/01/2000")!!
        val endXte = mdyToXteDate(entry.endDate) ?: mdyToXteDate("12/31/2030")!!
        val lookup = postLookup(entry.scoresUrl, sectionId, studentApiId, beginXte, endXte)
        val beginIso = isoFromXte(beginXte)
        val endIso = isoFromXte(endXte)
        return RawRecord(
            course = entry.courseName, teacher = entry.teacher, expression = entry.expression,
            termCode = entry.termCode, courseId = entry.courseId, gradeText = entry.gradeText,
            beginDate = entry.beginDate, endDate = entry.endDate, schoolId = entry.schoolId,
            assignments = mapLookupToAssignments(lookup, entry.courseId, beginIso, endIso),
        )
    }

    companion object {
        private const val UA = PS_UA

        fun parseSetCookie(header: String, defaultDomain: String): PsCookie? {
            val parts = header.split(';')
            if (parts.isEmpty()) return null
            val pair = parts[0]
            val eq = pair.indexOf('=')
            if (eq <= 0) return null
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isEmpty()) return null
            var domain = defaultDomain
            var path = "/"
            var expires: Long? = null
            var secure = false
            for (attr in parts.drop(1)) {
                val kv = attr.split('=', limit = 2)
                when (kv[0].trim().lowercase()) {
                    "expires" -> if (kv.size > 1) {
                        runCatching { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US).parse(kv[1].trim())?.time }
                            .getOrNull()?.let { expires = it }
                    }
                    "max-age" -> if (kv.size > 1) {
                        kv[1].trim().toLongOrNull()?.let { expires = System.currentTimeMillis() + it * 1000 }
                    }
                    "domain" -> if (kv.size > 1) domain = kv[1].trim().removePrefix(".").lowercase()
                    "path" -> if (kv.size > 1) path = kv[1].trim().ifEmpty { "/" }
                    "secure" -> secure = true
                }
            }
            return PsCookie(name, value, domain, path, expires, secure)
        }
    }
}
