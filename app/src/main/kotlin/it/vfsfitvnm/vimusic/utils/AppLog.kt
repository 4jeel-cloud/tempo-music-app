package it.vfsfitvnm.vimusic.utils

import android.content.Context
import android.os.Build
import android.util.Log
import it.vfsfitvnm.vimusic.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Small in-app logger.
 *
 * Everything is mirrored to logcat (tags [TAG]/subtag) and kept in a bounded in-memory ring
 * buffer so the user can share it from Settings > About without needing a PC or root
 * (Android 10+ does not allow apps to read other apps' logcat output).
 *
 * Must be initialised once with [init] (from Application.onCreate) so it knows the app context
 * for the persisted crash file.
 */
object AppLog {
    private const val TAG = "Tempo"
    private const val MAX_LINES = 4000
    private const val CRASH_FILE = "last_crash.txt"

    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    fun record(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val line = "${timeFormat.format(Date())} $level/$tag: $message"

        when (level) {
            "E" -> Log.e("$TAG/$tag", message, throwable)
            "W" -> Log.w("$TAG/$tag", message, throwable)
            "I" -> Log.i("$TAG/$tag", message, throwable)
            else -> Log.d("$TAG/$tag", message, throwable)
        }

        lines.addLast(line)
        throwable?.let { t ->
            stackTrace(t).trimEnd().lineSequence().forEach { lines.addLast("    $it") }
        }
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    fun d(tag: String, message: String) = record("D", tag, message)
    fun i(tag: String, message: String) = record("I", tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        record("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        record("E", tag, message, throwable)

    @Synchronized
    fun snapshot(): String = lines.joinToString(separator = "\n")

    @Synchronized
    fun clear() {
        lines.clear()
        appContext?.let { File(it.cacheDir, CRASH_FILE).delete() }
    }

    /** Persists the last uncaught exception together with the buffered log so it survives a crash. */
    fun persistCrash(thread: Thread, throwable: Throwable) {
        record("E", "Crash", "Uncaught exception on thread ${thread.name}", throwable)
        val context = appContext ?: return
        runCatching {
            File(context.cacheDir, CRASH_FILE).writeText(
                "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" +
                    snapshot()
            )
        }
    }

    private fun lastCrash(): String? {
        val context = appContext ?: return null
        return runCatching { File(context.cacheDir, CRASH_FILE).takeIf { it.exists() }?.readText() }
            .getOrNull()
    }

    /** Human readable report used by the "Share logs" action. */
    fun buildReport(): String {
        val crash = lastCrash()
        return buildString {
            appendLine("Tempo log report")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            if (!crash.isNullOrBlank()) {
                appendLine("===== LAST CRASH =====")
                appendLine(crash)
                appendLine()
            }
            appendLine("===== LOG (${lines.size} lines) =====")
            appendLine(snapshot())
        }
    }

    private fun stackTrace(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
