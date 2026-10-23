package it.vfsfitvnm.vimusic.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import it.vfsfitvnm.vimusic.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Information about a published app release that is newer than the running build.
 */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val pageUrl: String,
    val changelog: String,
    val sizeBytes: Long,
)

/**
 * Checks GitHub Releases for a newer build and downloads/installs its APK.
 *
 * The app is distributed as signed APKs on GitHub, so the latest release is the
 * source of truth. No backend or website is required.
 */
object Updater {
    private const val REPO = "4jeel-cloud/tempo-music-app"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val USER_AGENT = "Tempo-Android/${BuildConfig.VERSION_NAME}"

    /**
     * Fetches the latest release and returns it only when it is newer than
     * [BuildConfig.VERSION_NAME]. Returns null when up to date or on failure.
     */
    suspend fun checkForUpdates(): UpdateInfo? = checkForUpdatesResult().getOrNull()

    /**
     * Like [checkForUpdates], but keeps the failure so callers can tell "up to date"
     * (success with a null value) apart from a failed check.
     */
    suspend fun checkForUpdatesResult(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        AppLog.i("Update", "Checking GitHub releases (current v${BuildConfig.VERSION_NAME})")
        runCatching {
            val latest = fetchLatestRelease()
            when {
                latest == null -> {
                    AppLog.w("Update", "Latest release has no usable version or APK")
                    null
                }

                isNewer(latest.versionName, BuildConfig.VERSION_NAME) -> {
                    AppLog.i("Update", "Update available: v${latest.versionName}")
                    latest
                }

                else -> {
                    AppLog.i("Update", "Up to date (latest release is v${latest.versionName})")
                    null
                }
            }
        }.onFailure {
            AppLog.e("Update", "Check failed: ${it.message}", it)
        }
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        val connection = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            if (connection.responseCode !in 200..299) return null

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (tag.isEmpty()) return null

            val assets = json.optJSONArray("assets")
            val apkAsset = (0 until (assets?.length() ?: 0))
                .mapNotNull { assets?.optJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
                // Prefer a universal build when several APKs are attached.
                .maxByOrNull { asset ->
                    val name = asset.optString("name").lowercase()
                    when {
                        "universal" in name -> 2
                        "arm64" in name || "armeabi" in name -> 1
                        else -> 0
                    }
                }

            val apkUrl = apkAsset?.optString("browser_download_url").orEmpty()
            if (apkUrl.isEmpty()) return null

            return UpdateInfo(
                versionName = tag,
                apkUrl = apkUrl,
                pageUrl = json.optString("html_url"),
                changelog = json.optString("body").trim(),
                sizeBytes = apkAsset?.optLong("size") ?: 0L,
            )
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the release APK into the external cache and returns the file, or null on failure. */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit = {},
    ): File? = withContext(Dispatchers.IO) {
        if (info.apkUrl.isEmpty()) return@withContext null

        val dir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "tempo-${info.versionName}.apk")

        // Reuse a previous download (e.g. when retrying after granting the install permission).
        if (target.exists() &&
            target.length() > 0 &&
            (info.sizeBytes <= 0L || target.length() == info.sizeBytes)
        ) {
            return@withContext target
        }

        // Clear stale downloads from other versions before writing a new one.
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }

        AppLog.i("Update", "Downloading v${info.versionName} (${info.sizeBytes} bytes)")
        runCatching {
            val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val total = connection.contentLength.takeIf { it > 0 }?.toLong() ?: info.sizeBytes

                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }

                // A truncated file would fail the installer with a confusing error.
                if (total > 0 && target.length() < total) {
                    target.delete()
                    return@runCatching null
                }

                target
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            AppLog.e("Update", "Download failed: ${it.message}", it)
            target.delete()
        }.getOrNull()?.also {
            AppLog.i("Update", "Downloaded v${info.versionName} (${it.length()} bytes)")
        }
    }

    /** Whether the OS will currently let this app launch its own package installer. */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Opens the "install unknown apps" settings page for this app. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /**
     * Launches the system package installer for a downloaded APK. Returns false when the
     * install permission still has to be granted by the user.
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) {
            AppLog.w("Update", "APK to install is missing: ${apk.absolutePath}")
            return false
        }
        if (!canInstall(context)) {
            AppLog.w("Update", "Install permission not granted, opening settings")
            requestInstallPermission(context)
            return false
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            AppLog.i("Update", "Launching system installer for v${apk.name}")
            true
        }.onFailure {
            AppLog.e("Update", "Could not launch installer: ${it.message}", it)
        }.getOrDefault(false)
    }

    internal fun isNewer(remote: String, local: String): Boolean = compare(remote, local) > 0

    /** Compares dotted versions numerically, returning a positive number when [a] is newer. */
    private fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        version.split('.', '-', '+', '_')
            .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
}
