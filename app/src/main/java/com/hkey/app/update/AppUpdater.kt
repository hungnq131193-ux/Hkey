package com.hkey.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

data class Release(
    val tag: String,
    val version: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?
)

typealias StreamOpener = (String) -> InputStream

object AppUpdater {
    const val LATEST_URL =
        "https://api.github.com/repos/hungnq131193-ux/Hkey/releases/latest"
    const val OWNER = "hungnq131193-ux"
    const val REPO = "Hkey"
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val TIMEOUT_MS = 15_000
    private val TAG_RE = Regex("^v(\\d+)\\.(\\d+)\\.(\\d+)$")
    private val SHA256_RE = Regex("^[0-9a-fA-F]{64}$")

    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.')
        val pb = b.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: return -1
            val y = pb.getOrNull(i)?.toIntOrNull() ?: return 1
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun parseRelease(json: String): Release? {
        val o = try { JSONObject(json) } catch (e: Exception) { return null }
        if (!o.has("draft") || !o.has("prerelease")) return null
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name", "")
        val m = TAG_RE.matchEntire(tag) ?: return null
        if (m.groupValues.drop(1).any { it.toIntOrNull() == null }) return null
        val version = m.groupValues.drop(1).joinToString(".")
        val page = o.optString("html_url", "")
        if (!page.equals(
                "https://github.com/$OWNER/$REPO/releases/tag/$tag",
                ignoreCase = false
            )
        ) return null
        val assets = o.optJSONArray("assets") ?: return null
        val want = "HKey-$version.apk"
        val wantPath = "/$OWNER/$REPO/releases/download/$tag/$want"
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name") != want) continue
            val url = a.optString("browser_download_url", "")
            if (!validApkUrl(url)) return null
            val path = try { java.net.URI(url).path } catch (e: Exception) { null }
                ?: return null
            val segs = path.split('/').filter { it.isNotEmpty() }
            if (segs[0] != OWNER || segs[1] != REPO ||
                segs[4] != tag || segs[5] != want || path != wantPath
            ) return null
            val size = a.optLong("size", -1)
            if (size <= 0 || size > MAX_APK_BYTES) return null
            val digest = if (a.isNull("digest")) null else a.optString("digest")
            val sha = when {
                digest == null -> null
                digest.startsWith("sha256:") &&
                    SHA256_RE.matches(digest.removePrefix("sha256:")) ->
                    digest.removePrefix("sha256:")
                else -> return null
            }
            return Release(tag, version, url, size, sha)
        }
        return null
    }

    fun validApkUrl(url: String): Boolean {
        val u = try { java.net.URI(url) } catch (e: Exception) { return false }
        if (!u.scheme.equals("https", true)) return false
        if (!u.host.equals("github.com", true)) return false
        if (u.userInfo != null) return false
        if (u.port != -1 && u.port != 443) return false
        if (u.rawQuery != null || u.rawFragment != null) return false
        val raw = u.rawPath ?: return false
        if (raw.contains('%')) return false
        val segs = raw.split('/').filter { it.isNotEmpty() }
        if (segs.size != 6) return false
        if (segs.any { it == ".." }) return false
        if (!segs[0].equals(OWNER, true) || !segs[1].equals(REPO, true)) return false
        if (segs[2] != "releases" || segs[3] != "download") return false
        if (!TAG_RE.matches(segs[4])) return false
        if (!segs[5].startsWith("HKey-") || !segs[5].endsWith(".apk")) return false
        return true
    }

    private fun conn(url: String): HttpsURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    fun fetchText(url: String): String {
        val c = conn(url)
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun openHttps(url: String): InputStream {
        val c = conn(url)
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return object : java.io.FilterInputStream(c.inputStream) {
                override fun close() {
                    try { super.close() } finally { c.disconnect() }
                }
            }
        } catch (t: Throwable) {
            c.disconnect()
            throw t
        }
    }

    fun updateDir(context: Context): File =
        File(context.cacheDir, "updates").apply { mkdirs() }

    suspend fun download(
        url: String,
        expectedSize: Long,
        sha256: String?,
        dest: File,
        openStream: StreamOpener,
        onProgress: (Long) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (!validApkUrl(url)) throw java.io.IOException("bad url")
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        dest.delete()
        part.delete()
        var ins: InputStream? = null
        try {
            ins = openStream(url)
            val md = sha256?.let { MessageDigest.getInstance("SHA-256") }
            var total = 0L
            val buf = ByteArray(64 * 1024)
            part.outputStream().buffered().use { out ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md?.update(buf, 0, n)
                    total += n
                    if (total > expectedSize) throw java.io.IOException("oversize")
                    onProgress(total)
                }
            }
            if (total != expectedSize) {
                throw java.io.IOException("size $total != $expectedSize")
            }
            if (md != null) {
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(sha256, true)) {
                    throw java.io.IOException("sha256 mismatch")
                }
            }
            if (!part.renameTo(dest)) throw java.io.IOException("rename failed")
            dest
        } catch (t: Throwable) {
            part.delete()
            dest.delete()
            throw t
        } finally {
            try { ins?.close() } catch (e: Exception) {}
        }
    }

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun manageUnknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
