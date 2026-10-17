package it.vfsfitvnm.innertube

import it.vfsfitvnm.innertube.models.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Resolves a directly playable audio URL for a YouTube video id using NewPipeExtractor.
 *
 * The original InnerTube player request stopped working because YouTube now requires proof-of-
 * origin tokens and stream URL signature deobfuscation, which ViMusic never implemented.
 * NewPipeExtractor is actively maintained and handles all of that internally.
 *
 * The result is mapped back to ViMusic's own [PlayerResponse] so the rest of the app (cache,
 * ExoPlayer data source, database `Format` insert, ...) keeps working unchanged.
 */
object NewPipeStreamResolver {
    /** Must be called once (from [android.app.Application.onCreate]) before [resolve]. */
    fun init() {
        NewPipe.init(NewPipeDownloader())
    }

    suspend fun resolve(videoId: String): PlayerResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )

            // PlayerResponse.highestQualityFormat only recognises itag 251 (opus) and 140 (m4a),
            // so restrict to those; otherwise the rest of the app would ignore our stream.
            val candidate = info.audioStreams
                .filter { it.itag == 251 || it.itag == 140 }
                .mapNotNull { stream ->
                    runCatching { stream.url }.getOrNull()?.let { url -> stream to url }
                }
                .maxByOrNull { it.first.bestBitrate }

            if (candidate == null) {
                InnertubeLog.error(
                    "NewPipe",
                    "No playable audio stream (itag 251/140) for $videoId; available itags=" +
                        info.audioStreams.map { it.itag },
                    null
                )
                return@runCatching null
            }

            val (stream, url) = candidate

            InnertubeLog.info(
                "NewPipe",
                "Resolved $videoId itag=${stream.itag} bitrate=${stream.bitrate} url=${url.substringBefore('?')}"
            )

            PlayerResponse(
                playabilityStatus = PlayerResponse.PlayabilityStatus("OK"),
                playerConfig = null,
                streamingData = PlayerResponse.StreamingData(
                    adaptiveFormats = listOf(
                        PlayerResponse.StreamingData.AdaptiveFormat(
                            itag = stream.itag,
                            mimeType = stream.format?.mimeType ?: "audio/mp4",
                            bitrate = stream.bitrate.toLong(),
                            averageBitrate = stream.averageBitrate.takeIf { it > 0 }?.toLong(),
                            contentLength = null,
                            audioQuality = stream.quality,
                            approxDurationMs = info.duration.takeIf { it > 0 }?.times(1000L),
                            lastModified = null,
                            loudnessDb = null,
                            audioSampleRate = null,
                            url = url
                        )
                    )
                ),
                videoDetails = PlayerResponse.VideoDetails(videoId)
            )
        }.onFailure { throwable ->
            InnertubeLog.error("NewPipe", "Resolution failed for $videoId", throwable)
        }.getOrNull()
    }

    private val AudioStream.bestBitrate: Int
        get() = averageBitrate.takeIf { it > 0 } ?: bitrate
}
