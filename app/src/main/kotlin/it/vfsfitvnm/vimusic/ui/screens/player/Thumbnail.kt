package it.vfsfitvnm.vimusic.ui.screens.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import coil.compose.AsyncImage
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.service.LoginRequiredException
import it.vfsfitvnm.vimusic.service.PlayableFormatNotFoundException
import it.vfsfitvnm.vimusic.service.UnplayableException
import it.vfsfitvnm.vimusic.service.VideoIdMismatchException
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.px
import it.vfsfitvnm.vimusic.utils.currentWindow
import it.vfsfitvnm.vimusic.utils.DisposableListener
import it.vfsfitvnm.vimusic.utils.rememberPreference
import it.vfsfitvnm.vimusic.utils.swipeToChangeSongKey
import it.vfsfitvnm.vimusic.utils.thumbnail
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    isShowingLyrics: Boolean,
    onShowLyrics: (Boolean) -> Unit,
    isShowingStatsForNerds: Boolean,
    onShowStatsForNerds: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val player = binder?.player ?: return

    val swipeToChangeSong by rememberPreference(swipeToChangeSongKey, true)

    val (thumbnailSizeDp, thumbnailSizePx) = Dimensions.thumbnails.player.song.let {
        it to (it - 64.dp).px
    }

    var nullableWindow by remember {
        mutableStateOf(player.currentWindow)
    }

    var error by remember {
        mutableStateOf<PlaybackException?>(player.playerError)
    }

    var timelineRevision by remember {
        mutableStateOf(0)
    }

    player.DisposableListener {
        object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                nullableWindow = player.currentWindow
                timelineRevision++
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nullableWindow = player.currentWindow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                error = player.playerError
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                error = playbackException
            }
        }
    }

    val window = nullableWindow ?: return

    val timeline = remember(timelineRevision, window.mediaItem.mediaId) {
        player.currentTimeline
    }
    val currentIndex = player.currentMediaItemIndex
    val shuffleModeEnabled = player.shuffleModeEnabled
    val previousIndex = if (!timeline.isEmpty) {
        timeline.getPreviousWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
    } else {
        C.INDEX_UNSET
    }
    val nextIndex = if (!timeline.isEmpty) {
        timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
    } else {
        C.INDEX_UNSET
    }
    val previousMediaItem = if (previousIndex != C.INDEX_UNSET) {
        runCatching { player.getMediaItemAt(previousIndex) }.getOrNull()
    } else {
        null
    }
    val nextMediaItem = if (nextIndex != C.INDEX_UNSET) {
        runCatching { player.getMediaItemAt(nextIndex) }.getOrNull()
    } else {
        null
    }
    val mediaItems = listOfNotNull(previousMediaItem, window.mediaItem, nextMediaItem)
    val currentMediaIndex = mediaItems.indexOf(window.mediaItem)
    val latestMediaItems = rememberUpdatedState(mediaItems)
    val latestCurrentMediaIndex = rememberUpdatedState(currentMediaIndex)

    val thumbnailGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = currentMediaIndex.coerceAtLeast(0)
    )
    val swipeEnabled =
        swipeToChangeSong && !isShowingLyrics && !isShowingStatsForNerds && error == null

    val thumbnailSnapLayoutInfoProvider = remember(thumbnailGridState) {
        ThumbnailSnapLayoutInfoProvider(
            lazyGridState = thumbnailGridState,
            positionInLayout = { layoutSize, itemSize ->
                layoutSize / 2f - itemSize / 2f
            }
        )
    }

    LaunchedEffect(
        window.mediaItem.mediaId,
        mediaItems.map(MediaItem::mediaId),
        currentMediaIndex,
        swipeEnabled
    ) {
        if (!swipeEnabled) {
            return@LaunchedEffect
        }

        val index = currentMediaIndex
        if (index in mediaItems.indices && !thumbnailGridState.isScrollInProgress) {
            thumbnailGridState.scrollToItem(index)
        }
    }

    LaunchedEffect(swipeEnabled) {
        if (!swipeEnabled) {
            return@LaunchedEffect
        }

        snapshotFlow {
            Triple(
                thumbnailGridState.isScrollInProgress,
                thumbnailGridState.firstVisibleItemIndex,
                thumbnailGridState.firstVisibleItemScrollOffset
            )
        }
            .distinctUntilChanged()
            .drop(1)
            .collect { (isScrolling, firstVisibleItemIndex, scrollOffset) ->
                if (isScrolling || scrollOffset != 0) {
                    return@collect
                }

                val index = latestCurrentMediaIndex.value
                if (index < 0) {
                    return@collect
                }

                val settledItem = latestMediaItems.value.getOrNull(firstVisibleItemIndex)
                    ?: return@collect
                if (settledItem.mediaId == player.currentMediaItem?.mediaId) {
                    return@collect
                }

                if (firstVisibleItemIndex > index && player.hasNextMediaItem()) {
                    player.seekToNext()
                } else if (firstVisibleItemIndex < index && player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .size(thumbnailSizeDp)
    ) {
        val itemWidth = maxWidth

        LazyHorizontalGrid(
            state = thumbnailGridState,
            rows = GridCells.Fixed(1),
            flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
            userScrollEnabled = swipeEnabled,
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = mediaItems,
                key = { it.mediaId }
            ) { item ->
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(LocalAppearance.current.thumbnailShape)
                        .pointerInput(item.mediaId) {
                            detectTapGestures(
                                onTap = {
                                    if (item.mediaId == window.mediaItem.mediaId) {
                                        onShowLyrics(true)
                                    }
                                },
                                onLongPress = {
                                    if (item.mediaId == window.mediaItem.mediaId) {
                                        onShowStatsForNerds(true)
                                    }
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = item.mediaMetadata.artworkUri.thumbnail(thumbnailSizePx),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(LocalAppearance.current.thumbnailShape)
        ) {
            Lyrics(
                mediaId = window.mediaItem.mediaId,
                isDisplayed = isShowingLyrics && error == null,
                onDismiss = { onShowLyrics(false) },
                ensureSongInserted = { Database.insert(window.mediaItem) },
                size = thumbnailSizeDp,
                mediaMetadataProvider = window.mediaItem::mediaMetadata,
                durationProvider = player::getDuration,
            )

            StatsForNerds(
                mediaId = window.mediaItem.mediaId,
                isDisplayed = isShowingStatsForNerds && error == null,
                onDismiss = { onShowStatsForNerds(false) }
            )

            PlaybackError(
                isDisplayed = error != null,
                messageProvider = {
                    val chain = generateSequence(error as Throwable?) { it.cause }.toList()
                    when {
                        chain.any { it is UnresolvedAddressException || it is UnknownHostException } ->
                            "A network error has occurred"
                        chain.any { it is PlayableFormatNotFoundException } ->
                            "Couldn't find a playable audio format"
                        chain.any { it is UnplayableException } ->
                            "The original video source of this song has been deleted"
                        chain.any { it is LoginRequiredException } ->
                            "This song cannot be played due to server restrictions"
                        chain.any { it is VideoIdMismatchException } ->
                            "The returned video id doesn't match the requested one"
                        else -> {
                            val serverStatus = chain
                                .filterIsInstance<PlaybackException>()
                                .mapNotNull { it.errorCodeName }
                                .firstOrNull { it != "ERROR_CODE_REMOTE_ERROR" && it != "ERROR_CODE_UNSPECIFIED" }
                            val deepest = chain.lastOrNull { it !is PlaybackException && it.message != null }

                            when {
                                deepest != null ->
                                    "Playback failed: ${deepest.javaClass.simpleName}: ${deepest.message?.take(160)}"
                                serverStatus != null ->
                                    "Playback error: $serverStatus"
                                else -> "An unknown playback error has occurred"
                            }
                        }
                    }
                },
                onDismiss = player::prepare
            )
        }
    }
}
