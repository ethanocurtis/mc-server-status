package com.mcserverstatus.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val AUTO_REFRESH_INTERVAL_SECONDS = intPreferencesKey("auto_refresh_interval_seconds")
    }

    val autoRefreshEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.AUTO_REFRESH_ENABLED] ?: false }

    val autoRefreshIntervalSeconds: Flow<Int> =
        context.dataStore.data.map { it[Keys.AUTO_REFRESH_INTERVAL_SECONDS] ?: DEFAULT_INTERVAL_SECONDS }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_REFRESH_ENABLED] = enabled }
    }

    suspend fun setAutoRefreshIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.AUTO_REFRESH_INTERVAL_SECONDS] = seconds }
    }

    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 30
        val AVAILABLE_INTERVALS_SECONDS = listOf(15, 30, 60, 120, 300)
    }
}
