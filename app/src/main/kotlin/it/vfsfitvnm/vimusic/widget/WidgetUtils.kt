package it.vfsfitvnm.vimusic.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import it.vfsfitvnm.vimusic.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetUtils {
    val appScope = CoroutineScope(Dispatchers.IO)

    // Must match PlayerService.Action values (handled by its registered receiver)
    const val ACTION_PLAY = "it.vfsfitvnm.vimusic.play"
    const val ACTION_PAUSE = "it.vfsfitvnm.vimusic.pause"
    const val ACTION_NEXT = "it.vfsfitvnm.vimusic.next"
    const val ACTION_PREVIOUS = "it.vfsfitvnm.vimusic.previous"

    const val ACTION_LIKE = "it.vfsfitvnm.vimusic.widget.LIKE"
    const val ACTION_PLAY_TARGET = "it.vfsfitvnm.vimusic.widget.PLAY_TARGET"

    const val EXTRA_TARGET_TYPE = "widget_target_type"
    const val EXTRA_TARGET_ID = "widget_target_id"

    const val TARGET_TYPE_PLAYLIST = "playlist"
    const val TARGET_TYPE_FAVORITES = "favorites"
    const val TARGET_TYPE_TOP = "top"

    private val immutableFlag: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    suspend fun loadBitmap(context: Context, uri: String, size: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(size, size)
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).drawable?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }

    fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        if (squareBitmap != bitmap) squareBitmap.recycle()
        return output
    }

    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            shader = BitmapShader(squareBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (squareBitmap != bitmap) squareBitmap.recycle()
        return output
    }

    private var defaultAppIcon: Bitmap? = null
    private val roundedDefaultCache = HashMap<Float, Bitmap>()
    private var circularDefaultCache: Bitmap? = null

    fun appIconBitmap(context: Context): Bitmap {
        defaultAppIcon?.let { return it }
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        defaultAppIcon = bitmap
        return bitmap
    }

    fun getRoundedDefaultIcon(context: Context, cornerRadius: Float): Bitmap =
        roundedDefaultCache.getOrPut(cornerRadius) {
            getRoundedCornerBitmap(appIconBitmap(context), cornerRadius)
        }

    fun getCircularDefaultIcon(context: Context): Bitmap =
        circularDefaultCache ?: getCircularBitmap(appIconBitmap(context)).also { circularDefaultCache = it }

    fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )

    fun controlPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )

    fun broadcastPendingIntent(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
        )
}

object WidgetState {
    @Volatile var hasState = false
    @Volatile var title: String = ""
    @Volatile var artist: String = ""
    @Volatile var artworkUri: String? = null
    @Volatile var isPlaying: Boolean = false
    @Volatile var isLiked: Boolean = false
    @Volatile var duration: Long = 0
    @Volatile var position: Long = 0
    @Volatile var songId: String? = null

    fun update(
        title: String,
        artist: String,
        artworkUri: String?,
        isPlaying: Boolean,
        isLiked: Boolean,
        duration: Long,
        position: Long,
        songId: String?
    ) {
        hasState = true
        this.title = title
        this.artist = artist
        this.artworkUri = artworkUri
        this.isPlaying = isPlaying
        this.isLiked = isLiked
        this.duration = duration
        this.position = position
        this.songId = songId
    }

    fun reset() {
        hasState = false
        title = ""
        artist = ""
        artworkUri = null
        isPlaying = false
        isLiked = false
        duration = 0
        position = 0
        songId = null
    }
}

object WidgetRefresh {
    suspend fun refresh(context: Context) {
        val appContext = context.applicationContext
        MusicWidgetManager.render(appContext)
        PlaylistWidgetManager.render(appContext)
    }

    suspend fun updatePlayback(context: Context) {
        val appContext = context.applicationContext
        MusicWidgetManager.render(appContext)
        PlaylistWidgetManager.updatePlayerSection(appContext)
    }

    fun updateProgress(context: Context, duration: Long, position: Long) {
        val appContext = context.applicationContext
        MusicWidgetManager.updateProgress(appContext, duration, position)
        PlaylistWidgetManager.updateProgress(appContext, duration, position)
    }
}
