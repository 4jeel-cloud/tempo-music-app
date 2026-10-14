package it.vfsfitvnm.vimusic.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import it.vfsfitvnm.vimusic.R

object MusicWidgetManager {
    @Volatile private var cachedArtworkUri: String? = null
    @Volatile private var cachedAlbumArt: Bitmap? = null
    @Volatile private var cachedRoundedAlbumArt: Bitmap? = null
    @Volatile private var cachedCircularAlbumArt: Bitmap? = null

    private suspend fun loadAlbumArt(context: Context): Bitmap? {
        val uri = WidgetState.artworkUri
        if (uri == null) {
            cachedArtworkUri = null
            cachedAlbumArt = null
            cachedRoundedAlbumArt = null
            cachedCircularAlbumArt = null
            return null
        }
        if (uri != cachedArtworkUri || cachedAlbumArt == null) {
            val loaded = WidgetUtils.loadBitmap(context, uri, 300)
            cachedAlbumArt = loaded
            cachedRoundedAlbumArt = loaded?.let { WidgetUtils.getRoundedCornerBitmap(it, 48f) }
            cachedCircularAlbumArt = loaded?.let { WidgetUtils.getCircularBitmap(it) }
            cachedArtworkUri = if (loaded != null) uri else null
        }
        return cachedAlbumArt
    }

    suspend fun render(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val title = WidgetState.title.ifEmpty { context.getString(R.string.not_playing) }
        val artist = WidgetState.artist.ifEmpty { context.getString(R.string.tap_to_play) }
        val albumArt = loadAlbumArt(context)
        val roundedAlbumArt = if (albumArt != null) cachedRoundedAlbumArt else null
        val circularAlbumArt = if (albumArt != null) cachedCircularAlbumArt else null
        val isPlaying = WidgetState.isPlaying
        val isLiked = WidgetState.isLiked
        val duration = WidgetState.duration
        val position = WidgetState.position

        appWidgetManager.getAppWidgetIds(ComponentName(context, MusicWidgetReceiver::class.java))
            .forEach { widgetId ->
                val options = appWidgetManager.getAppWidgetOptions(widgetId)
                appWidgetManager.updateAppWidget(
                    widgetId,
                    createForSize(context, options, title, artist, roundedAlbumArt, isPlaying, isLiked, duration, position)
                )
            }

        appWidgetManager.getAppWidgetIds(ComponentName(context, TurntableWidgetReceiver::class.java))
            .forEach { widgetId ->
                appWidgetManager.updateAppWidget(
                    widgetId,
                    createTurntable(context, circularAlbumArt, isPlaying, isLiked)
                )
            }
    }

    fun updateProgress(context: Context, duration: Long, position: Long) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager
            .getAppWidgetIds(ComponentName(context, MusicWidgetReceiver::class.java))
            .filter { widgetId ->
                appWidgetManager.getAppWidgetOptions(widgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) >= 100
            }
            .toIntArray()

        if (widgetIds.isEmpty()) return

        val level = if (duration > 0) {
            ((position.toDouble() / duration) * 10000).toInt().coerceIn(0, 10000)
        } else {
            0
        }
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)
        views.setInt(R.id.widget_progress_fill, "setImageLevel", level)
        appWidgetManager.partiallyUpdateAppWidget(widgetIds, views)
    }

    private fun createForSize(
        context: Context,
        options: Bundle,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        position: Long
    ): RemoteViews {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        return when {
            minWidth < 180 && minHeight < 100 -> createCompactSquare(context, albumArt, isPlaying)
            minWidth >= 180 && minHeight < 100 -> createCompactWide(context, title, artist, albumArt, isPlaying, isLiked)
            else -> createFull(context, title, artist, albumArt, isPlaying, isLiked, duration, position)
        }
    }

    private fun createFull(
        context: Context,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        position: Long
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_music_player)

        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        views.setImageViewBitmap(
            R.id.widget_album_art,
            albumArt ?: WidgetUtils.getRoundedDefaultIcon(context, 48f)
        )

        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_like_button,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )

        val level = if (duration > 0) {
            ((position.toDouble() / duration) * 10000).toInt().coerceIn(0, 10000)
        } else {
            0
        }
        views.setInt(R.id.widget_progress_fill, "setImageLevel", level)

        views.setOnClickPendingIntent(R.id.widget_album_art, WidgetUtils.openAppPendingIntent(context, 0))
        views.setOnClickPendingIntent(R.id.widget_play_pause_container, playPauseIntent(context, isPlaying, 1))
        views.setOnClickPendingIntent(R.id.widget_like_button, likePendingIntent(context))

        return views
    }

    private fun createCompactSquare(context: Context, albumArt: Bitmap?, isPlaying: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_square)

        views.setImageViewBitmap(
            R.id.widget_compact_album_art,
            albumArt ?: WidgetUtils.getRoundedDefaultIcon(context, 48f)
        )

        views.setImageViewResource(
            R.id.widget_compact_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        )

        views.setOnClickPendingIntent(R.id.widget_compact_album_art, WidgetUtils.openAppPendingIntent(context, 0))
        views.setOnClickPendingIntent(R.id.widget_compact_play_container, playPauseIntent(context, isPlaying, 1))

        return views
    }

    private fun createCompactWide(
        context: Context,
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_compact_wide)

        views.setTextViewText(R.id.widget_wide_song_title, title)
        views.setTextViewText(R.id.widget_wide_artist_name, artist)

        views.setImageViewBitmap(
            R.id.widget_wide_album_art,
            albumArt ?: WidgetUtils.getRoundedDefaultIcon(context, 48f)
        )

        views.setImageViewResource(
            R.id.widget_wide_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause_low else R.drawable.ic_widget_play_low
        )
        views.setImageViewResource(
            R.id.widget_wide_like_button,
            if (isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )

        views.setOnClickPendingIntent(R.id.widget_wide_album_art, WidgetUtils.openAppPendingIntent(context, 0))
        views.setOnClickPendingIntent(R.id.widget_wide_play_container, playPauseIntent(context, isPlaying, 1))
        views.setOnClickPendingIntent(R.id.widget_wide_like_button, likePendingIntent(context))

        return views
    }

    private fun createTurntable(
        context: Context,
        circularAlbumArt: Bitmap?,
        isPlaying: Boolean,
        @Suppress("UNUSED_PARAMETER") isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        if (circularAlbumArt != null) {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, circularAlbumArt)
        } else {
            views.setImageViewBitmap(R.id.widget_turntable_album_art, WidgetUtils.getCircularDefaultIcon(context))
        }

        views.setImageViewResource(
            R.id.widget_turntable_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause_secondary else R.drawable.ic_widget_play_secondary
        )

        views.setOnClickPendingIntent(R.id.widget_turntable_album_art, WidgetUtils.openAppPendingIntent(context, 0))
        views.setOnClickPendingIntent(R.id.widget_turntable_play_container, playPauseIntent(context, isPlaying, 3))
        views.setOnClickPendingIntent(
            R.id.widget_turntable_prev_button,
            WidgetUtils.controlPendingIntent(context, WidgetUtils.ACTION_PREVIOUS, 4)
        )
        views.setOnClickPendingIntent(
            R.id.widget_turntable_next_button,
            WidgetUtils.controlPendingIntent(context, WidgetUtils.ACTION_NEXT, 5)
        )

        return views
    }

    private fun playPauseIntent(context: Context, isPlaying: Boolean, requestCode: Int) =
        WidgetUtils.controlPendingIntent(
            context,
            if (isPlaying) WidgetUtils.ACTION_PAUSE else WidgetUtils.ACTION_PLAY,
            requestCode
        )

    private fun likePendingIntent(context: Context) =
        WidgetUtils.broadcastPendingIntent(
            context,
            android.content.Intent(context, MusicWidgetReceiver::class.java).setAction(WidgetUtils.ACTION_LIKE),
            2
        )
}
