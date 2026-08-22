package com.mcserverstatus.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcserverstatus.app.data.ServerEntry
import com.mcserverstatus.app.data.SettingsRepository
import com.mcserverstatus.app.ui.MainViewModel
import com.mcserverstatus.app.ui.PingUiState
import com.mcserverstatus.app.ui.components.AddEditServerDialog
import com.mcserverstatus.app.ui.components.EmptyState
import com.mcserverstatus.app.ui.components.ServerCard

private sealed interface DialogMode {
    data object Add : DialogMode
    data class Edit(val entry: ServerEntry) : DialogMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    viewModel: MainViewModel,
    onOpenServer: (Long) -> Unit,
) {
    val servers by viewModel.uiState.collectAsStateWithLifecycle()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsStateWithLifecycle()
    val autoRefreshInterval by viewModel.autoRefreshIntervalSeconds.collectAsStateWithLifecycle()

    var dialogMode by remember { mutableStateOf<DialogMode?>(null) }
    var pendingDelete by remember { mutableStateOf<ServerEntry?>(null) }
    var autoRefreshMenuExpanded by remember { mutableStateOf(false) }

    val isRefreshing = servers.isNotEmpty() && servers.any { it.ping is PingUiState.Loading }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MC Server Status") },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh all")
                    }
                    IconButton(onClick = { autoRefreshMenuExpanded = true }) {
                        Icon(Icons.Filled.Sync, contentDescription = "Auto-refresh settings")
                    }
                    DropdownMenu(
                        expanded = autoRefreshMenuExpanded,
                        onDismissRequest = { autoRefreshMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Auto refresh") },
                            trailingIcon = {
                                Switch(
                                    checked = autoRefreshEnabled,
                                    onCheckedChange = { viewModel.setAutoRefreshEnabled(it) },
                                )
                            },
                            onClick = { viewModel.setAutoRefreshEnabled(!autoRefreshEnabled) },
                        )
                        SettingsRepository.AVAILABLE_INTERVALS_SECONDS.forEach { seconds ->
                            DropdownMenuItem(
                                text = { Text("Every ${formatInterval(seconds)}") },
                                trailingIcon = {
                                    if (seconds == autoRefreshInterval) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = { viewModel.setAutoRefreshIntervalSeconds(seconds) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogMode = DialogMode.Add }) {
                Icon(Icons.Filled.Add, contentDescription = "Add server")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (servers.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(servers, key = { it.entry.id }) { state ->
                        ServerCard(
                            state = state,
                            onClick = { onOpenServer(state.entry.id) },
                            onEdit = { dialogMode = DialogMode.Edit(state.entry) },
                            onDelete = { pendingDelete = state.entry },
                            onRefresh = { viewModel.refreshOne(state.entry) },
                        )
                    }
                }
            }
        }
    }

    when (val mode = dialogMode) {
        null -> Unit
        DialogMode.Add -> AddEditServerDialog(
            existing = null,
            onDismiss = { dialogMode = null },
            onSave = { name, host, port, edition ->
                viewModel.addServer(name, host, port, edition)
                dialogMode = null
            },
        )
        is DialogMode.Edit -> AddEditServerDialog(
            existing = mode.entry,
            onDismiss = { dialogMode = null },
            onSave = { name, host, port, edition ->
                viewModel.updateServer(mode.entry.copy(name = name, host = host, port = port, edition = edition))
                dialogMode = null
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove server?") },
            text = { Text("\"${entry.displayName}\" will be removed from your list.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteServer(entry)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

private fun formatInterval(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "${seconds / 60}m"
