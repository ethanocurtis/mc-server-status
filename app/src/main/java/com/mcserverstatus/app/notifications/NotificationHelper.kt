package com.mcserverstatus.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mcserverstatus.app.R
import com.mcserverstatus.app.data.ServerEntry

object NotificationHelper {

    const val CHANNEL_ID = "server_status_changes"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Server status changes",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifies you when a watched server goes online or offline"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posts a "went offline"/"back online" notification for [entry]. No-ops silently if the
     * app doesn't (or, pre-API 33, doesn't need to) hold POST_NOTIFICATIONS. */
    fun notifyStatusChange(context: Context, entry: ServerEntry, nowOnline: Boolean) {
        val hasPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val title = if (nowOnline) {
            "${entry.displayName} is back online"
        } else {
            "${entry.displayName} went offline"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(entry.addressLabel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Distinct IDs per direction (not just per server) so an "offline" notification and a
        // later "back online" one for the same server show as two separate notifications
        // instead of the second silently overwriting the first before you ever see it. A given
        // server can never fire the same direction twice in a row - notifyStatusChange is only
        // ever called on an actual flip - so this can't accumulate beyond one card per direction.
        val notificationId = (entry.id * 2 + if (nowOnline) 1 else 0).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
