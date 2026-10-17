package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.largestThumbnail
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask(
            "contents.singleColumnBrowseResultsRenderer.tabs.tabRenderer.content.sectionListRenderer.contents(" +
                "musicShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "musicCarouselShelfRenderer.contents.$musicTwoRowItemRendererMask)," +
                "contents.twoColumnBrowseResultsRenderer(" +
                "tabs.tabRenderer.content.sectionListRenderer.contents." +
                "musicResponsiveHeaderRenderer(title,subtitle,straplineTextOne,secondSubtitle,thumbnail)," +
                "secondaryContents.sectionListRenderer.contents(" +
                "musicShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "musicPlaylistShelfRenderer(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "musicCarouselShelfRenderer.contents.$musicTwoRowItemRendererMask))," +
                "header.musicDetailHeaderRenderer(title,subtitle,secondSubtitle,thumbnail),microformat"
        )
    }.body<BrowseResponse>()

    val singleColumnContents = response.contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents

    val twoColumnBrowseResultsRenderer = response.contents
        ?.twoColumnBrowseResultsRenderer

    val twoColumnTabContents = twoColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer
        ?.contents

    val twoColumnSecondaryContents = twoColumnBrowseResultsRenderer
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents

    // Older responses carried the header at the top level (musicDetailHeaderRenderer); the newer
    // two-column layout puts a musicResponsiveHeaderRenderer inside the tab's section list instead.
    val musicDetailHeaderRenderer = response.header
        ?.musicDetailHeaderRenderer

    val musicResponsiveHeaderRenderer = twoColumnTabContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicResponsiveHeaderRenderer)

    val sectionListRendererContents = singleColumnContents
        ?: twoColumnSecondaryContents

    val musicShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicShelfRenderer)

    val musicCarouselShelfRenderer = sectionListRendererContents
        ?.firstNotNullOfOrNull(SectionListRenderer.Content::musicCarouselShelfRenderer)

    Innertube.PlaylistOrAlbumPage(
        title = musicResponsiveHeaderRenderer
            ?.title
            ?.text
            ?: musicDetailHeaderRenderer
                ?.title
                ?.text,
        thumbnail = (musicResponsiveHeaderRenderer?.thumbnail ?: musicDetailHeaderRenderer?.thumbnail)
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.largestThumbnail(),
        authors = musicResponsiveHeaderRenderer
            ?.straplineTextOne
            ?.splitBySeparator()
            ?.flatten()
            ?.map(Innertube::Info)
            ?: musicDetailHeaderRenderer
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(1)
                ?.map(Innertube::Info),
        year = musicResponsiveHeaderRenderer
            ?.subtitle
            ?.splitBySeparator()
            ?.getOrNull(1)
            ?.firstOrNull()
            ?.text
            ?: musicDetailHeaderRenderer
                ?.subtitle
                ?.splitBySeparator()
                ?.getOrNull(2)
                ?.firstOrNull()
                ?.text,
        url = response
            .microformat
            ?.microformatDataRenderer
            ?.urlCanonical,
        songsPage = musicShelfRenderer
            ?.toSongsPage(),
        otherVersions = musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        // Playlists answer with musicPlaylistShelfContinuation while albums answer with
        // musicShelfContinuation; request both so the mask never filters the payload to null.
        mask(
            "continuationContents(" +
                "musicPlaylistShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)," +
                "musicShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask))"
        )
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toSongsPage()
}

private fun MusicShelfRenderer?.toSongsPage() =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )
