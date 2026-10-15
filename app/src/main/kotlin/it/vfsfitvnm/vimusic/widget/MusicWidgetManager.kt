package it.vfsfitvnm.vimusic.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.utils.thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MusicWidgetManager {
    private const val TURNTABLE_ART_SIZE = 1000

    @Volatile private var cachedArtworkUri: String? = null
    @Volatile private var cachedAlbumArt: Bitmap? = null
    @Volatile private var cachedRoundedAlbumArt: Bitmap? = null

    @Volatile private var cachedTurntableSourceUri: String? = null
    @Volatile private var cachedTurntableArtUri: Uri? = null

    private suspend fun loadAlbumArt(context: Context): Bitmap? {
        val uri = WidgetState.artworkUri
        if (uri == null) {
            cachedArtworkUri = null
            cachedAlbumArt = null
            cachedRoundedAlbumArt = null
            return null
        }
        if (uri != cachedArtworkUri || cachedAlbumArt == null) {
            val loaded = WidgetUtils.loadBitmap(context, uri, 300)
            cachedAlbumArt = loaded
            cachedRoundedAlbumArt = loaded?.let { WidgetUtils.getRoundedCornerBitmap(it, 48f) }
            cachedArtworkUri = if (loaded != null) uri else null
        }
        return cachedAlbumArt
    }

    /**
     * Renders the current artwork as a high resolution circular PNG and returns the content Uri that
     * the home screen can load. This is used by the turntable widget, which can grow to 4x4 and would
     * otherwise show an upscaled (blurry) low resolution bitmap.
     */
    private suspend fun loadTurntableArt(context: Context): Uri? {
        val sourceUri = WidgetState.artworkUri
        if (sourceUri == null) {
            cachedTurntableSourceUri = null
            cachedTurntableArtUri = null
            return null
        }
        if (sourceUri == cachedTurntableSourceUri && cachedTurntableArtUri != null) {
            return cachedTurntableArtUri
        }

        val highRes = sourceUri.thumbnail(TURNTABLE_ART_SIZE) ?: sourceUri
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = WidgetUtils.loadBitmap(context, highRes, TURNTABLE_ART_SIZE)
                    ?: return@runCatching null
                val circular = WidgetUtils.getCircularBitmap(bitmap)

                val dir = File(context.cacheDir, WidgetArtProvider.DIR).apply { mkdirs() }
                val name = "art_${Integer.toHexString(highRes.hashCode())}.png"
                File(dir, name).outputStream().use { circular.compress(Bitmap.CompressFormat.PNG, 100, it) }
                dir.listFiles()?.forEach { if (it.name != name) it.delete() }

                WidgetArtProvider.uriFor(context.packageName, name)
            }.getOrNull()
        }

        cachedTurntableSourceUri = sourceUri
        cachedTurntableArtUri = result
        if (result != null) grantHomeScreenAccess(context, result)
        return result
    }

    private fun grantHomeScreenAccess(context: Context, uri: Uri) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolveInfo ->
                runCatching {
                    context.grantUriPermission(
                        resolveInfo.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
    }

    suspend fun render(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val title = WidgetState.title.ifEmpty { context.getString(R.string.not_playing) }
        val artist = WidgetState.artist.ifEmpty { context.getString(R.string.tap_to_play) }
        val albumArt = loadAlbumArt(context)
        val roundedAlbumArt = if (albumArt != null) cachedRoundedAlbumArt else null
        val turntableArt = loadTurntableArt(context)
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
                    createTurntable(context, turntableArt, isPlaying, isLiked)
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
        artUri: Uri?,
        isPlaying: Boolean,
        @Suppress("UNUSED_PARAMETER") isLiked: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_turntable)

        if (artUri != null) {
            views.setImageViewUri(R.id.widget_turntable_album_art, artUri)
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
