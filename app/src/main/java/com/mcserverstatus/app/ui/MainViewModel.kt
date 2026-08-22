package com.mcserverstatus.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mcserverstatus.app.McServerStatusApp
import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.data.ServerEntry
import com.mcserverstatus.app.data.ServerRepository
import com.mcserverstatus.app.data.SettingsRepository
import com.mcserverstatus.app.network.ServerPinger
import com.mcserverstatus.app.network.ServerStatusResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Per-server ping state, kept separately from the persisted [ServerEntry] list. */
sealed interface PingUiState {
    data object Loading : PingUiState
    data class Loaded(val result: ServerStatusResult) : PingUiState
}

data class ServerUiState(
    val entry: ServerEntry,
    val ping: PingUiState,
)

class MainViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val pingStates = MutableStateFlow<Map<Long, PingUiState>>(emptyMap())

    private val serversState: StateFlow<List<ServerEntry>> =
        serverRepository.servers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<List<ServerUiState>> =
        combine(serversState, pingStates) { servers, pings ->
            servers.map { ServerUiState(it, pings[it.id] ?: PingUiState.Loading) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val autoRefreshEnabled: StateFlow<Boolean> =
        settingsRepository.autoRefreshEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val autoRefreshIntervalSeconds: StateFlow<Int> =
        settingsRepository.autoRefreshIntervalSeconds.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsRepository.DEFAULT_INTERVAL_SECONDS,
        )

    init {
        // Ping any server as soon as it appears (first load from disk, or right after adding one).
        viewModelScope.launch {
            var knownIds = emptySet<Long>()
            serversState.collect { servers ->
                val newOnes = servers.filter { it.id !in knownIds }
                knownIds = servers.map { it.id }.toSet()
                newOnes.forEach { refreshOne(it) }
            }
        }
        viewModelScope.launch { autoRefreshLoop() }
    }

    private suspend fun autoRefreshLoop() {
        while (true) {
            if (autoRefreshEnabled.value) {
                refreshAll()
                kotlinx.coroutines.delay(autoRefreshIntervalSeconds.value * 1_000L)
            } else {
                kotlinx.coroutines.delay(1_000L)
            }
        }
    }

    fun refreshAll() {
        serversState.value.forEach { refreshOne(it) }
    }

    fun refreshOne(entry: ServerEntry) {
        viewModelScope.launch {
            pingStates.update { it + (entry.id to PingUiState.Loading) }
            val result = ServerPinger.ping(entry)
            pingStates.update { it + (entry.id to PingUiState.Loaded(result)) }
        }
    }

    fun addServer(name: String, host: String, port: Int, edition: ServerEdition) {
        viewModelScope.launch {
            serverRepository.add(ServerEntry(name = name, host = host, port = port, edition = edition))
        }
    }

    fun updateServer(entry: ServerEntry) {
        viewModelScope.launch {
            serverRepository.update(entry)
            refreshOne(entry)
        }
    }

    fun deleteServer(entry: ServerEntry) {
        viewModelScope.launch {
            serverRepository.delete(entry)
            pingStates.update { it - entry.id }
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoRefreshEnabled(enabled) }
    }

    fun setAutoRefreshIntervalSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setAutoRefreshIntervalSeconds(seconds) }
    }

    companion object {
        fun factory(app: McServerStatusApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app.serverRepository, app.settingsRepository) as T
                }
            }
    }
}
