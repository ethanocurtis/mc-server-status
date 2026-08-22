package com.mcserverstatus.app

import android.app.Application
import com.mcserverstatus.app.data.AppDatabase
import com.mcserverstatus.app.data.ServerRepository
import com.mcserverstatus.app.data.SettingsRepository

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
    }
}
