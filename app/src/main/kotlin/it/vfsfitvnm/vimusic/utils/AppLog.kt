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
 * Everything is mirrored to logcat (tags [TAG]/subtag) and kept in a bounded buffer that is
 * periodically flushed to [filesDir]/[LOG_FILE], so a report still contains what happened before a
 * restart or a force stop (Android 10+ does not allow apps to read other apps' logcat output).
 *
 * Must be initialised once with [init] (from Application.onCreate).
 */
object AppLog {
    private const val TAG = "Tempo"
    private const val MAX_LINES = 4000
    private const val MAX_FILE_BYTES = 512 * 1024
    private const val FLUSH_EVERY = 20
    private const val CRASH_FILE = "last_crash.txt"
    private const val LOG_FILE = "diagnostics.log"

    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        i("App", "Started v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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

        // Errors are exactly what a report needs to survive a restart, so write them straight away.
        if (level == "E" || lines.size >= FLUSH_EVERY) flushLocked()
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
    fun flush() = flushLocked()

    @Synchronized
    fun clear() {
        lines.clear()
        appContext?.let { context ->
            runCatching { File(context.filesDir, LOG_FILE).delete() }
            runCatching { File(context.cacheDir, CRASH_FILE).delete() }
        }
    }

    /** Persists the last uncaught exception together with the buffered log so it survives a crash. */
    @Synchronized
    fun persistCrash(thread: Thread, throwable: Throwable) {
        record("E", "Crash", "Uncaught exception on thread ${thread.name}", throwable)

        val context = appContext ?: return
        runCatching {
            File(context.filesDir, CRASH_FILE).writeText(
                "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" +
                    "${throwable.javaClass.name}: ${throwable.message}\n" +
                    stackTrace(throwable).trimEnd()
            )
        }
    }

    private fun lastCrash(): String? {
        val context = appContext ?: return null
        return runCatching { File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText() }
            .getOrNull()
    }

    /** Human readable report used by the "Share logs" action. */
    @Synchronized
    fun buildReport(): String {
        flushLocked()

        val persisted = readLogLocked()
        val crash = lastCrash()
        val total = if (persisted.isBlank()) 0 else persisted.trimEnd('\n').split('\n').size

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
            appendLine("===== LOG ($total lines) =====")
            appendLine(persisted.trimEnd('\n'))
        }
    }

    private fun flushLocked() {
        val context = appContext ?: return
        if (lines.isEmpty()) return

        runCatching {
            val file = File(context.filesDir, LOG_FILE)
            file.parentFile?.mkdirs()

            file.appendText(buildString {
                lines.forEach { append(it).append('\n') }
            })

            lines.clear()
            trimLocked(file)
        }
    }

    private fun readLogLocked(): String {
        val context = appContext ?: return ""
        return runCatching {
            File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText()
        }.getOrNull() ?: ""
    }

    private fun trimLocked(file: File) {
        runCatching {
            if (file.length() <= MAX_FILE_BYTES) return

            val bytes = file.readBytes()
            if (bytes.size <= MAX_FILE_BYTES) return

            var start = bytes.size - MAX_FILE_BYTES
            while (start < bytes.size && bytes[start].toInt() != 10) start++
            if (start >= bytes.size) return

            file.writeBytes(bytes.copyOfRange(start + 1, bytes.size))
        }
    }

    private fun stackTrace(throwable: Throwable): String {
        val stringWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stringWriter))
        return stringWriter.toString()
    }
}
