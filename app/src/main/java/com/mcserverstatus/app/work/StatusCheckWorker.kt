package com.mcserverstatus.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mcserverstatus.app.data.AppDatabase
import com.mcserverstatus.app.network.ServerPinger
import com.mcserverstatus.app.notifications.NotificationHelper
import com.mcserverstatus.app.widget.WidgetUpdater

/**
 * Periodic background check for servers the user has opted into status-change
 * notifications for. Pings each one, and if its online/offline state flipped
 * since the last check, posts a notification and persists the new state.
 * Also refreshes the home screen widget (see [WidgetUpdater]) each cycle.
 *
 * Deliberately only touches servers with `notifyOnStatusChange = true` - this
 * is a background *notifier*, not a general background refresher; the
 * foreground app's own pings are unrelated and don't feed into this state.
 */
class StatusCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).serverDao()
        val watched = dao.getNotifyEnabled()

        for (entry in watched) {
            val nowOnline = ServerPinger.ping(entry, timeoutMs = CHECK_TIMEOUT_MS).success
            val previouslyOnline = entry.lastKnownOnline

            if (previouslyOnline != nowOnline) {
                if (previouslyOnline != null) {
                    NotificationHelper.notifyStatusChange(applicationContext, entry, nowOnline)
                }
                dao.update(entry.copy(lastKnownOnline = nowOnline))
            }
        }

        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "server-status-check"
        private const val CHECK_TIMEOUT_MS = 8_000
    }
}
