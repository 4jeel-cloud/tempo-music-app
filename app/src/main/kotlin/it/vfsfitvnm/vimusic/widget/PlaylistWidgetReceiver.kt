package it.vfsfitvnm.vimusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import it.vfsfitvnm.vimusic.MainActivity
import it.vfsfitvnm.vimusic.service.PlayerService
import kotlinx.coroutines.launch

class PlaylistWidgetReceiver : AppWidgetProvider() {
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
        if (intent.action == WidgetUtils.ACTION_PLAY_TARGET) {
            handlePlayTarget(context.applicationContext, intent)
        }
    }

    private fun handlePlayTarget(context: Context, intent: Intent) {
        val type = intent.getStringExtra(WidgetUtils.EXTRA_TARGET_TYPE) ?: return
        val id = intent.getStringExtra(WidgetUtils.EXTRA_TARGET_ID) ?: return

        if (PlayerService.isRunning) {
            val forward = Intent(WidgetUtils.ACTION_PLAY_TARGET)
                .setPackage(context.packageName)
                .putExtra(WidgetUtils.EXTRA_TARGET_TYPE, type)
                .putExtra(WidgetUtils.EXTRA_TARGET_ID, id)
            context.sendBroadcast(forward)
        } else {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
