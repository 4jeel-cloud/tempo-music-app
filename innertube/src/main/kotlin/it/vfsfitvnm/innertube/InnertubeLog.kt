package it.vfsfitvnm.innertube

/**
 * Pluggable logger for the pure-JVM innertube module.
 *
 * This module has no Android dependencies, so playback-resolution diagnostics are reported
 * through these hooks and wired to the app's [it.vfsfitvnm.vimusic.utils.AppLog] in
 * `Application.onCreate`.
 */
object InnertubeLog {
    var error: (tag: String, message: String, throwable: Throwable?) -> Unit = { _, _, _ -> }
    var info: (tag: String, message: String) -> Unit = { _, _ -> }
}
