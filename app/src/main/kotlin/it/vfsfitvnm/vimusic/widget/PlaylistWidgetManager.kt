package it.vfsfitvnm.vimusic.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Song
import kotlinx.coroutines.flow.first

private data class QuickPick(
    val key: String,
    val type: String,
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val fallbackIconRes: Int
)

object PlaylistWidgetManager {
    @Volatile private var cachedPlayerArtUri: String? = null
    @Volatile private var cachedPlayerArt: Bitmap? = null

    private val cardIds = intArrayOf(
        R.id.widget_playlist_card_1, R.id.widget_playlist_card_2,
        R.id.widget_playlist_card_3, R.id.widget_playlist_card_4,
        R.id.widget_playlist_card_5, R.id.widget_playlist_card_6,
        R.id.widget_playlist_card_7, R.id.widget_playlist_card_8
    )
    private val cardArtIds = intArrayOf(
        R.id.widget_playlist_card_1_art, R.id.widget_playlist_card_2_art,
        R.id.widget_playlist_card_3_art, R.id.widget_playlist_card_4_art,
        R.id.widget_playlist_card_5_art, R.id.widget_playlist_card_6_art,
        R.id.widget_playlist_card_7_art, R.id.widget_playlist_card_8_art
    )
    private val cardPlayContainerIds = intArrayOf(
        R.id.widget_playlist_card_1_play_container, R.id.widget_playlist_card_2_play_container,
        R.id.widget_playlist_card_3_play_container, R.id.widget_playlist_card_4_play_container,
        R.id.widget_playlist_card_5_play_container, R.id.widget_playlist_card_6_play_container,
        R.id.widget_playlist_card_7_play_container, R.id.widget_playlist_card_8_play_container
    )
    private val cardTitleIds = intArrayOf(
        R.id.widget_playlist_card_1_title, R.id.widget_playlist_card_2_title,
        R.id.widget_playlist_card_3_title, R.id.widget_playlist_card_4_title,
        R.id.widget_playlist_card_5_title, R.id.widget_playlist_card_6_title,
        R.id.widget_playlist_card_7_title, R.id.widget_playlist_card_8_title
    )

    suspend fun render(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PlaylistWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val quickPicks = buildQuickPicks(context)
        ids.forEach { widgetId ->
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            appWidgetManager.updateAppWidget(widgetId, createRemoteViews(context, options, quickPicks))
        }
    }

    suspend fun updatePlayerSection(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PlaylistWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_playlist)
        bindPlayerSection(context, views)
        appWidgetManager.partiallyUpdateAppWidget(ids, views)
    }

    fun updateProgress(context: Context, duration: Long, position: Long) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PlaylistWidgetReceiver::class.java))
        if (ids.isEmpty()) return

        val level = if (duration > 0) {
            ((position.toDouble() / duration) * 10000).toInt().coerceIn(0, 10000)
        } else {
            0
        }
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)
        views.setInt(R.id.widget_playlist_progress_fill, "setImageLevel", level)
        appWidgetManager.partiallyUpdateAppWidget(ids, views)
    }

    private suspend fun buildQuickPicks(context: Context): List<QuickPick> {
        val picks = mutableListOf<QuickPick>()
        runCatching {
            Database.playlistPreviewsByNameAsc().first()
        }.getOrDefault(emptyList()).take(8).forEach { preview ->
            val thumbnail = runCatching {
                Database.playlistThumbnailUrls(preview.playlist.id).first().firstOrNull()
            }.getOrNull()
            picks += QuickPick(
                key = "playlist:${preview.playlist.id}",
                type = WidgetUtils.TARGET_TYPE_PLAYLIST,
                id = preview.playlist.id.toString(),
                title = preview.playlist.name,
                thumbnailUrl = thumbnail,
                fallbackIconRes = R.drawable.playlist_play
            )
        }

        val favorites = runCatching { Database.favorites().first() }.getOrDefault(emptyList())
        if (favorites.isNotEmpty()) {
            picks += QuickPick(
                key = "auto:favorites",
                type = WidgetUtils.TARGET_TYPE_FAVORITES,
                id = "favorites",
                title = context.getString(R.string.liked_songs),
                thumbnailUrl = favorites.first().thumbnailUrl,
                fallbackIconRes = R.drawable.ic_widget_heart_nav
            )
        }

        val top = runCatching { Database.songsByPlayTimeDesc().first() }.getOrDefault(emptyList())
        if (top.isNotEmpty()) {
            picks += QuickPick(
                key = "auto:top",
                type = WidgetUtils.TARGET_TYPE_TOP,
                id = "top",
                title = context.getString(R.string.my_top),
                thumbnailUrl = top.first().thumbnailUrl,
                fallbackIconRes = R.drawable.trending_up
            )
        }

        return picks.distinctBy { it.key }
    }

    suspend fun resolveTargetSongIds(type: String, id: String): List<Song> {
        return when (type) {
            WidgetUtils.TARGET_TYPE_PLAYLIST -> {
                val playlistId = id.toLongOrNull() ?: return emptyList()
                Database.playlistWithSongs(playlistId).first()?.songs ?: emptyList()
            }
            WidgetUtils.TARGET_TYPE_FAVORITES -> Database.favorites().first()
            WidgetUtils.TARGET_TYPE_TOP -> Database.songsByPlayTimeDesc().first()
            else -> emptyList()
        }
    }

    private fun maxQuickPicksForSize(options: Bundle): Int {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        return if (minWidth >= 320 && minHeight >= 250) 8 else 4
    }

    private suspend fun createRemoteViews(
        context: Context,
        options: Bundle,
        quickPicks: List<QuickPick>
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_playlist)
        bindPlayerSection(context, views)
        bindQuickPicks(context, views, options, quickPicks)
        return views
    }

    private suspend fun bindPlayerSection(context: Context, views: RemoteViews) {
        views.setTextViewText(
            R.id.widget_playlist_song_title,
            WidgetState.title.ifEmpty { context.getString(R.string.not_playing) }
        )
        views.setTextViewText(
            R.id.widget_playlist_artist_name,
            WidgetState.artist.ifEmpty { context.getString(R.string.choose_something_below) }
        )

        views.setImageViewBitmap(R.id.widget_playlist_album_art, roundedPlayerArt(context))

        views.setImageViewResource(
            R.id.widget_playlist_play_pause,
            if (WidgetState.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )
        views.setImageViewResource(
            R.id.widget_playlist_like_button,
            if (WidgetState.isLiked) R.drawable.ic_widget_heart_nav else R.drawable.ic_widget_heart_outline_nav
        )

        val level = if (WidgetState.duration > 0) {
            ((WidgetState.position.toDouble() / WidgetState.duration) * 10000).toInt().coerceIn(0, 10000)
        } else {
            0
        }
        views.setInt(R.id.widget_playlist_progress_fill, "setImageLevel", level)

        views.setOnClickPendingIntent(R.id.widget_playlist_album_art, WidgetUtils.openAppPendingIntent(context, 0))
        views.setOnClickPendingIntent(R.id.widget_playlist_player_section, WidgetUtils.openAppPendingIntent(context, 0))
        views.setOnClickPendingIntent(
            R.id.widget_playlist_play_pause_container,
            WidgetUtils.controlPendingIntent(
                context,
                if (WidgetState.isPlaying) WidgetUtils.ACTION_PAUSE else WidgetUtils.ACTION_PLAY,
                1
            )
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_prev_container,
            WidgetUtils.controlPendingIntent(context, WidgetUtils.ACTION_PREVIOUS, 4)
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_next_container,
            WidgetUtils.controlPendingIntent(context, WidgetUtils.ACTION_NEXT, 5)
        )
        views.setOnClickPendingIntent(
            R.id.widget_playlist_like_button,
            WidgetUtils.broadcastPendingIntent(
                context,
                Intent(context, MusicWidgetReceiver::class.java).setAction(WidgetUtils.ACTION_LIKE),
                2
            )
        )
    }

    private suspend fun roundedPlayerArt(context: Context): Bitmap {
        val uri = WidgetState.artworkUri
        if (uri == cachedPlayerArtUri && cachedPlayerArt != null) return cachedPlayerArt!!
        val art = uri?.let { WidgetUtils.loadBitmap(context, it, 180) }
            ?.let { WidgetUtils.getRoundedCornerBitmap(it, 36f) }
            ?: WidgetUtils.getRoundedDefaultIcon(context, 36f)
        cachedPlayerArtUri = uri
        cachedPlayerArt = art
        return art
    }

    private suspend fun bindQuickPicks(
        context: Context,
        views: RemoteViews,
        options: Bundle,
        quickPicks: List<QuickPick>
    ) {
        val slots = maxQuickPicksForSize(options)
        val count = minOf(quickPicks.size, slots)
        val twoRows = slots == 8

        views.setViewVisibility(
            R.id.widget_playlist_quick_picks_section,
            if (count > 0) View.VISIBLE else View.GONE
        )
        views.setViewVisibility(
            R.id.widget_playlist_row_2,
            if (twoRows && count > 4) View.VISIBLE else View.GONE
        )

        for (i in 0 until slots) {
            if (i < count) {
                val pick = quickPicks[i]
                views.setViewVisibility(cardIds[i], View.VISIBLE)
                views.setTextViewText(cardTitleIds[i], pick.title)

                val bitmap = pick.thumbnailUrl?.let { WidgetUtils.loadBitmap(context, it, 180) }
                    ?.let { WidgetUtils.getRoundedCornerBitmap(it, 36f) }
                    ?: fallbackArtBitmap(context, pick.fallbackIconRes)
                views.setImageViewBitmap(cardArtIds[i], bitmap)

                val playIntent = Intent(context, PlaylistWidgetReceiver::class.java)
                    .setAction(WidgetUtils.ACTION_PLAY_TARGET)
                    .putExtra(WidgetUtils.EXTRA_TARGET_TYPE, pick.type)
                    .putExtra(WidgetUtils.EXTRA_TARGET_ID, pick.id)
                views.setOnClickPendingIntent(
                    cardPlayContainerIds[i],
                    WidgetUtils.broadcastPendingIntent(context, playIntent, 100 + i)
                )
                views.setOnClickPendingIntent(cardArtIds[i], WidgetUtils.openAppPendingIntent(context, 200 + i))
                views.setOnClickPendingIntent(cardIds[i], WidgetUtils.openAppPendingIntent(context, 300 + i))
            } else {
                views.setViewVisibility(cardIds[i], View.GONE)
            }
        }
    }

    private fun fallbackArtBitmap(context: Context, iconRes: Int): Bitmap {
        val size = 180
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(context, R.color.widget_tertiary_container)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 36f, 36f, paint)

        val icon = ContextCompat.getDrawable(context, iconRes)
        if (icon != null) {
            val inset = size / 4
            icon.setBounds(inset, inset, size - inset, size - inset)
            icon.draw(canvas)
        }
        return bitmap
    }
}
