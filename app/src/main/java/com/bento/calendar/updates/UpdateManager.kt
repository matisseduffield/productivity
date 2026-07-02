package com.bento.calendar.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.bento.calendar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater backed by the app's public GitHub releases. Checks the latest
 * release tag against the installed version, streams the APK asset to cache,
 * and hands it to [PackageInstaller] — the OS verifies the signature matches
 * the installed app, so data is preserved and nothing unsigned can install.
 */
object UpdateManager {
    private const val OWNER = "matisseduffield"
    private const val REPO = "productivity"
    private const val LATEST_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    /** Outcome of a check — distinguishes "up to date" from "couldn't check". */
    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object UpToDate : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /**
     * Terminal install-failure message from [UpdateInstallReceiver], surfaced
     * to the UI. Process-static so the manifest receiver can publish without a
     * ViewModel reference.
     */
    val installError = MutableStateFlow<String?>(null)

    /** Piecewise numeric compare of dotted versions: "1.10.0" > "1.9.9". */
    fun isNewer(remote: String, installed: String): Boolean {
        val r = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val i = installed.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (k in 0 until maxOf(r.size, i.size)) {
            val a = r.getOrElse(k) { 0 }
            val b = i.getOrElse(k) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) return@withContext CheckResult.Failed("Couldn't reach GitHub (HTTP $code)")
            val root = Json.parseToJsonElement(
                conn.inputStream.bufferedReader().readText(),
            ).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content
                ?: return@withContext CheckResult.Failed("No releases found")
            val remote = tag.removePrefix("v")
            if (!isNewer(remote, BuildConfig.VERSION_NAME)) return@withContext CheckResult.UpToDate
            val apk = root["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return@withContext CheckResult.Failed("Release has no APK yet")
            CheckResult.Available(
                UpdateInfo(
                    versionName = remote,
                    apkUrl = apk["browser_download_url"]?.jsonPrimitive?.content
                        ?: return@withContext CheckResult.Failed("Release asset missing URL"),
                    sizeBytes = apk["size"]?.jsonPrimitive?.long ?: -1L,
                ),
            )
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: "Update check failed")
        } finally {
            conn.disconnect()
        }
    }

    /** Streams the APK to app cache, reporting progress 0..1. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "update-${info.versionName}.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) error("Download failed: HTTP ${conn.responseCode}")
            val total = if (info.sizeBytes > 0) info.sizeBytes else conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { sink ->
                    val buf = ByteArray(64 * 1024)
                    var read = input.read(buf)
                    var done = 0L
                    while (read >= 0) {
                        sink.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        read = input.read(buf)
                    }
                }
            }
            out
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Commits the APK through PackageInstaller on IO; Android shows the confirm
     * UI and enforces the signature match. Abandons any stale sessions first and
     * cleans up on failure so retries never accumulate orphaned sessions.
     */
    suspend fun install(context: Context, apk: File): Unit = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller

        // Reject an APK that isn't actually this app before we ever prompt.
        val pkgInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        require(pkgInfo?.packageName == BuildConfig.APPLICATION_ID) {
            "Downloaded APK is not ${BuildConfig.APPLICATION_ID}"
        }

        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { sink ->
                        input.copyTo(sink)
                        session.fsync(sink)
                    }
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                    .setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
