package com.bento.calendar.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.bento.calendar.BuildConfig
import kotlinx.coroutines.Dispatchers
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

    /** Latest release if it is newer than the installed build, else null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val root = Json.parseToJsonElement(
                conn.inputStream.bufferedReader().readText(),
            ).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            val remote = tag.removePrefix("v")
            if (!isNewer(remote, BuildConfig.VERSION_NAME)) return@withContext null
            val apk = root["assets"]?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                ?: return@withContext null
            UpdateInfo(
                versionName = remote,
                apkUrl = apk["browser_download_url"]?.jsonPrimitive?.content ?: return@withContext null,
                sizeBytes = apk["size"]?.jsonPrimitive?.long ?: -1L,
            )
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

    /** Commits the APK through PackageInstaller; Android shows the confirm UI. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { sink ->
                    input.copyTo(sink)
                    session.fsync(sink)
                }
            }
            val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
            session.commit(pending.intentSender)
        }
    }
}
