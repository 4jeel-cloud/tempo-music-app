package it.vfsfitvnm.vimusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import it.vfsfitvnm.vimusic.Database
import kotlinx.coroutines.launch

class MusicWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUtils.appScope.launch { WidgetRefresh.refresh(context.applicationContext) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WidgetUtils.appScope.launch { WidgetRefresh.refresh(context.applicationContext) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUtils.ACTION_LIKE) {
            WidgetUtils.appScope.launch { toggleLike(context.applicationContext) }
        }
    }

    private suspend fun toggleLike(context: Context) {
        val songId = WidgetState.songId ?: return
        val newLikedAt = if (WidgetState.isLiked) null else System.currentTimeMillis()
        runCatching { Database.like(songId, newLikedAt) }
        WidgetState.isLiked = newLikedAt != null
        WidgetRefresh.updatePlayback(context)
    }
}
