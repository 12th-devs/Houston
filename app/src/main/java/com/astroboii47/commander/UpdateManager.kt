package com.astroboii47.commander

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app updates from GitHub pre-releases. Release tags look like
 * `v0.2.0-alpha.1+10` (versionName + versionCode); the newest non-draft
 * release whose code exceeds the installed one wins, and its `.apk` asset
 * is downloaded and installed through a FileProvider URI. Works for both
 * pre-releases and stable releases so the channel survives graduation.
 */
object UpdateManager {
    const val OWNER = "12th-devs"
    const val REPO = "Houston"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Long,
        val notes: String,
        val apkUrl: String,
        val tag: String,
    )

    sealed interface CheckResult {
        data object UpToDate : CheckResult
        data class Available(val info: UpdateInfo, val downloaded: File? = null) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    private val TAG_RE = Regex("^v(.+)\\+(\\d+)$")

    fun parseTag(tag: String): Pair<String, Long>? {
        val m = TAG_RE.matchEntire(tag.trim()) ?: return null
        val code = m.groupValues[2].toLongOrNull() ?: return null
        return Pair(m.groupValues[1], code)
    }

    /** Newest non-draft release newer than [installedCode], or null. Pure for tests. */
    fun pickUpdate(root: JVal, installedCode: Long): UpdateInfo? {
        val releases = (root as? JVal.Arr)?.l ?: return null
        var best: UpdateInfo? = null
        for (r in releases) {
            if (r !is JVal.Obj) continue
            if (r.objBool("draft") == true) continue
            val tag = r.objStr("tag_name") ?: continue
            val (name, code) = parseTag(tag) ?: continue
            if (code <= installedCode) continue
            if (best != null && code <= best.versionCode) continue
            val apk = (r.objArr("assets") ?: emptyList())
                .firstOrNull { it is JVal.Obj && (it.objStr("name") ?: "").endsWith(".apk", true) }
                ?.let { (it as JVal.Obj).objStr("browser_download_url") }
                ?: continue
            best = UpdateInfo(name, code, r.objStr("body") ?: "", apk, tag)
        }
        return best
    }

    private fun getText(urlStr: String): Pair<Int, String> {
        var url = URL(urlStr)
        repeat(6) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Houston-Android-Updater")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val status = try {
                conn.responseCode
            } catch (e: Exception) {
                conn.disconnect()
                throw IllegalStateException("Update check failed: ${e.message?.take(120)}")
            }
            if (status in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrEmpty()) throw IllegalStateException("Update check redirected without location.")
                url = URL(url, loc)
                return@repeat
            }
            val stream = if (status in 200..299) {
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
            }
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            conn.disconnect()
            return Pair(status, body)
        }
        throw IllegalStateException("Update check hit too many redirects.")
    }

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).let { pi ->
                if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
            }
            val (status, body) = getText("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=20")
            if (status == 403 || status == 429) {
                return@withContext CheckResult.Failed("GitHub rate limit hit — try again in a few minutes.")
            }
            if (status != 200) return@withContext CheckResult.Failed("Update check failed (HTTP $status).")
            val root = try {
                parseJson(body)
            } catch (_: Exception) {
                return@withContext CheckResult.Failed("Update check returned bad data.")
            }
            val found = pickUpdate(root, installed) ?: run {
                // Distinguish "no newer release" from malformed payloads.
                if (root !is JVal.Arr) return@withContext CheckResult.Failed("Update check returned bad data.")
                return@withContext CheckResult.UpToDate
            }
            val staged = stagedApk(context, found)
            CheckResult.Available(found, staged.takeIf { it.exists() && it.length() > 0 })
        } catch (e: Exception) {
            CheckResult.Failed(e.message?.take(200) ?: "Update check failed.")
        }
    }

    private fun updatesDir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun stagedApk(context: Context, info: UpdateInfo): File =
        File(updatesDir(context), "Houston-${info.tag.replace('/', '-')}.apk")

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dst = stagedApk(context, info)
            // Clean other staged versions first.
            updatesDir(context).listFiles()?.forEach { if (it != dst) it.delete() }
            var url = URL(info.apkUrl)
            var redirects = 0
            var c: HttpURLConnection? = null
            while (c == null) {
                if (++redirects > 6) throw IllegalStateException("Download hit too many redirects.")
                val cur = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "Houston-Android-Updater")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val status = try {
                    cur.responseCode
                } catch (e: Exception) {
                    cur.disconnect()
                    throw IllegalStateException("Download failed: ${e.message?.take(120)}")
                }
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val loc = cur.getHeaderField("Location")
                    cur.disconnect()
                    if (loc.isNullOrEmpty()) throw IllegalStateException("Download redirected without location.")
                    url = URL(url, loc)
                    continue
                }
                if (status !in 200..299) {
                    cur.disconnect()
                    throw IllegalStateException("Download failed (HTTP $status).")
                }
                c = cur
            }
            try {
                val total = c.contentLengthLong.takeIf { it > 0 } ?: -1L
                val tmp = File(updatesDir(context), dst.name + ".tmp")
                c.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastReport >= 512 * 1024 || done == total) {
                                lastReport = done
                                withContext(Dispatchers.Main) { onProgress(done, total) }
                            }
                        }
                    }
                }
                if (tmp.length() < 1_000_000) {
                    tmp.delete()
                    throw IllegalStateException("Downloaded file looks truncated.")
                }
                if (!tmp.renameTo(dst)) {
                    dst.delete()
                    tmp.renameTo(dst)
                }
                dst
            } finally {
                c.disconnect()
            }
        }

    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openUnknownSourcesSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
