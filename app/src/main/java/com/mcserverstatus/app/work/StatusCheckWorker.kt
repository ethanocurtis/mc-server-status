package com.mcserverstatus.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mcserverstatus.app.data.AppDatabase
import com.mcserverstatus.app.data.ServerEntry
import com.mcserverstatus.app.network.ServerPinger
import com.mcserverstatus.app.notifications.NotificationHelper
import com.mcserverstatus.app.widget.WidgetUpdater

/**
 * Periodic background check for servers the user has opted into status-change
 * notifications for. Pings each one and, once a new online/offline reading is
 * *confirmed* (see [resolveNewState]), posts a notification and persists the
 * new state. Also refreshes the home screen widget (see [WidgetUpdater]) each
 * cycle.
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
            val updated = resolveNewState(entry, nowOnline)
            if (updated != entry) {
                if (updated.lastKnownOnline != entry.lastKnownOnline && entry.lastKnownOnline != null) {
                    NotificationHelper.notifyStatusChange(applicationContext, entry, updated.lastKnownOnline == true)
                }
                dao.update(updated)
            }
        }

        WidgetUpdater.refresh(applicationContext)
        return Result.success()
    }

    /**
     * Debounced state transition: a reading that disagrees with the confirmed [ServerEntry.lastKnownOnline]
     * only gets confirmed (and only then triggers a notification) once the *same* disagreeing
     * reading shows up on a second, later check - see [ServerEntry.pendingStatusChange] for why.
     * Returns [entry] unchanged if there's nothing to persist this cycle.
     */
    private fun resolveNewState(entry: ServerEntry, nowOnline: Boolean): ServerEntry = when {
        entry.lastKnownOnline == null ->
            // First check ever for this server: establish a baseline, nothing to confirm yet.
            entry.copy(lastKnownOnline = nowOnline, pendingStatusChange = null)

        nowOnline == entry.lastKnownOnline ->
            // Matches the confirmed state. Clear a stale pending flag left over from an earlier
            // one-off blip that never got confirmed by a follow-up matching reading.
            if (entry.pendingStatusChange != null) entry.copy(pendingStatusChange = null) else entry

        entry.pendingStatusChange == nowOnline ->
            // Second consecutive check agreeing with a *different* state than what's confirmed:
            // this is a real transition, not a blip.
            entry.copy(lastKnownOnline = nowOnline, pendingStatusChange = null)

        else ->
            // First time seeing this disagreement - could be a real transition starting, or just
            // this check's own network hiccup. Wait for the next cycle to agree before acting.
            entry.copy(pendingStatusChange = nowOnline)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "server-status-check"
        private const val CHECK_TIMEOUT_MS = 8_000
    }
}
