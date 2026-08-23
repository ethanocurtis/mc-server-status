package com.mcserverstatus.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mcserverstatus.app.data.AppDatabase
import com.mcserverstatus.app.data.ServerRepository
import com.mcserverstatus.app.data.SettingsRepository
import com.mcserverstatus.app.notifications.NotificationHelper
import com.mcserverstatus.app.work.StatusCheckWorker
import java.util.concurrent.TimeUnit

/**
 * Simple hand-rolled service locator - this app is small enough that a full
 * DI framework would add more ceremony than value.
 */
class McServerStatusApp : Application() {

    lateinit var serverRepository: ServerRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        serverRepository = ServerRepository(database.serverDao())
        settingsRepository = SettingsRepository(this)

        NotificationHelper.ensureChannel(this)
        schedulePeriodicStatusCheck()
    }

    private fun schedulePeriodicStatusCheck() {
        // 15 minutes is WorkManager's own floor for periodic work; asking for less just gets
        // silently clamped up to it.
        val request = PeriodicWorkRequestBuilder<StatusCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            StatusCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
