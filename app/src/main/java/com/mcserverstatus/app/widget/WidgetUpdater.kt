package com.mcserverstatus.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.mcserverstatus.app.data.AppDatabase
import com.mcserverstatus.app.network.MotdFormatter
import com.mcserverstatus.app.network.ServerPinger

/**
 * Pings the favorited server (if any) and recomposes the home screen widget
 * with the result. Shared by [com.mcserverstatus.app.work.StatusCheckWorker]'s
 * periodic cycle, the widget's own tap-to-refresh action, and the app's "set
 * as favorite" flow, so the widget doesn't wait for the next 15-minute cycle
 * to reflect a newly-picked favorite.
 */
object WidgetUpdater {

    private const val REFRESH_TIMEOUT_MS = 8_000

    suspend fun refresh(context: Context) {
        val dao = AppDatabase.getInstance(context).serverDao()
        val favorite = dao.getFavorite()
        if (favorite != null) {
            val result = ServerPinger.ping(favorite, timeoutMs = REFRESH_TIMEOUT_MS)
            dao.update(
                favorite.copy(
                    lastKnownOnline = result.success,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastKnownPlayersOnline = result.playersOnline,
                    lastKnownPlayersMax = result.playersMax,
                    lastKnownLatencyMs = result.latencyMs,
                    lastKnownMotd = if (result.success) MotdFormatter.toPlainText(result.motd) else null,
                    lastKnownFaviconBase64 = result.faviconBase64,
                ),
            )
        }
        McStatusWidget().updateAll(context)
    }
}
