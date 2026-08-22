package com.mcserverstatus.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.ui.MainViewModel
import com.mcserverstatus.app.ui.PingUiState
import com.mcserverstatus.app.ui.components.FaviconImage
import com.mcserverstatus.app.ui.components.MotdText
import com.mcserverstatus.app.ui.theme.StatusOffline
import com.mcserverstatus.app.ui.theme.StatusOnline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    serverId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val servers by viewModel.uiState.collectAsStateWithLifecycle()
    val state = servers.firstOrNull { it.entry.id == serverId }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.entry?.displayName ?: "Server") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state != null) {
                        IconButton(onClick = { viewModel.refreshOne(state.entry) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This server was removed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val ping = state.ping
            val loaded = (ping as? PingUiState.Loaded)?.result

            FaviconImage(faviconBase64 = loaded?.faviconBase64, modifier = Modifier.size(96.dp))

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${state.entry.host}:${state.entry.port}", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString("${state.entry.host}:${state.entry.port}"))
                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy address", modifier = Modifier.size(18.dp))
                }
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(if (state.entry.edition == ServerEdition.BEDROCK) "Bedrock Edition" else "Java Edition") },
                modifier = Modifier.padding(top = 4.dp),
            )

            when (ping) {
                is PingUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                    Text(
                        "Pinging server…",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is PingUiState.Loaded -> {
                    val result = ping.result
                    if (result.success) {
                        Row(
                            modifier = Modifier.padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(StatusOnline),
                            )
                            Text("Online", style = MaterialTheme.typography.titleMedium, color = StatusOnline)
                        }

                        MotdText(
                            segments = result.motd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        )
                        if (result.motdLine2.isNotEmpty()) {
                            MotdText(segments = result.motdLine2, modifier = Modifier.fillMaxWidth())
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (result.playersOnline != null && result.playersMax != null) {
                                StatChip("Players", "${result.playersOnline}/${result.playersMax}")
                            }
                            result.latencyMs?.let { StatChip("Ping", "${it} ms") }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            result.versionName?.let { StatChip("Version", it) }
                            result.protocolVersion?.let { StatChip("Protocol", it.toString()) }
                            result.gamemode?.let { StatChip("Mode", it) }
                        }

                        if (result.playerSample.isNotEmpty()) {
                            Text(
                                "Players online",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(result.playerSample) { name ->
                                    SuggestionChip(onClick = {}, label = { Text(name) })
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(top = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = StatusOffline,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                "Offline",
                                style = MaterialTheme.typography.titleMedium,
                                color = StatusOffline,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                result.errorMessage ?: "Could not reach the server",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Button(
                                onClick = { viewModel.refreshOne(state.entry) },
                                modifier = Modifier.padding(top = 16.dp),
                            ) { Text("Try again") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    AssistChip(onClick = {}, enabled = false, label = { Text("$label: $value") })
}
