package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.InnertubeLog
import it.vfsfitvnm.innertube.NewPipeStreamResolver
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.serialization.Serializable

/**
 * Resolves the player response for [body].
 *
 * NewPipeExtractor is tried first because the legacy InnerTube request below no longer yields
 * playable URLs. If it fails, the old InnerTube path is used as a best-effort fallback.
 */
suspend fun Innertube.player(body: PlayerBody): Result<PlayerResponse>? {
    NewPipeStreamResolver.resolve(body.videoId)?.let {
        return Result.success(it)
    }
    InnertubeLog.error(
        "Player",
        "NewPipe returned no stream for ${body.videoId}; falling back to InnerTube",
        null
    )
    return playerFromInnerTube(body)
}

private suspend fun Innertube.playerFromInnerTube(body: PlayerBody) = runCatchingNonCancellable {
    val response = client.post(player) {
        setBody(body)
        mask("playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
    }.body<PlayerResponse>()

    InnertubeLog.info(
        "Player",
        "InnerTube status for ${body.videoId} = ${response.playabilityStatus?.status}"
    )

    if (response.playabilityStatus?.status == "OK") {
        response
    } else {
        @Serializable
        data class AudioStream(
            val url: String,
            val bitrate: Long
        )

        @Serializable
        data class PipedResponse(
            val audioStreams: List<AudioStream>
        )

        val safePlayerResponse = client.post(player) {
            setBody(
                body.copy(
                    context = Context.DefaultAgeRestrictionBypass.copy(
                        thirdParty = Context.ThirdParty(
                            embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                        )
                    ),
                )
            )
            mask("playabilityStatus.status,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
        }.body<PlayerResponse>()

        if (safePlayerResponse.playabilityStatus?.status != "OK") {
            InnertubeLog.error(
                "Player",
                "Age-restriction fallback status for ${body.videoId} = ${safePlayerResponse.playabilityStatus?.status}",
                null
            )
            return@runCatchingNonCancellable response
        }

        val audioStreams = runCatching {
            client.get("https://watchapi.whatever.social/streams/${body.videoId}") {
                contentType(ContentType.Application.Json)
            }.body<PipedResponse>().audioStreams
        }.onFailure { throwable ->
            InnertubeLog.error("Player", "Piped (whatever.social) lookup failed for ${body.videoId}", throwable)
        }.getOrElse { emptyList() }

        safePlayerResponse.copy(
            streamingData = safePlayerResponse.streamingData?.copy(
                adaptiveFormats = safePlayerResponse.streamingData.adaptiveFormats?.map { adaptiveFormat ->
                    adaptiveFormat.copy(
                        url = audioStreams.find { it.bitrate == adaptiveFormat.bitrate }?.url
                    )
                }
            )
        )
    }
}
