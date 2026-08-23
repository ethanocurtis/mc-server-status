package com.mcserverstatus.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.ui.PingUiState
import com.mcserverstatus.app.ui.ServerUiState
import com.mcserverstatus.app.ui.theme.FavoriteStar
import com.mcserverstatus.app.ui.theme.LatencyBad
import com.mcserverstatus.app.ui.theme.LatencyGood
import com.mcserverstatus.app.ui.theme.LatencyOk
import com.mcserverstatus.app.ui.theme.StatusOffline
import com.mcserverstatus.app.ui.theme.StatusOnline

@Composable
fun ServerCard(
    state: ServerUiState,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ping = state.ping
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val favicon = (ping as? PingUiState.Loaded)?.result?.faviconBase64
            FaviconImage(faviconBase64 = favicon, modifier = Modifier.size(56.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = state.entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.entry.addressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Box(modifier = Modifier.padding(top = 4.dp)) {
                    when (ping) {
                        is PingUiState.Loading ->
                            Text(
                                text = "Pinging…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        is PingUiState.Loaded -> {
                            val result = ping.result
                            if (result.success) {
                                MotdText(
                                    segments = result.motd,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    text = result.errorMessage ?: "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusOffline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                if (ping is PingUiState.Loaded && ping.result.success) {
                    StatusRow(
                        online = true,
                        playersOnline = ping.result.playersOnline,
                        playersMax = ping.result.playersMax,
                        latencyMs = ping.result.latencyMs,
                        edition = state.entry.edition,
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (state.entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (state.entry.isFavorite) "Unset as widget favorite" else "Show on home screen widget",
                    tint = if (state.entry.isFavorite) FavoriteStar else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                if (ping is PingUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuExpanded = false; onRefresh() })
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    online: Boolean,
    playersOnline: Int?,
    playersMax: Int?,
    latencyMs: Long?,
    edition: ServerEdition,
) {
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(online = online)

        if (playersOnline != null && playersMax != null) {
            IconLabel(icon = Icons.Filled.People, text = "$playersOnline/$playersMax")
        }

        if (latencyMs != null) {
            IconLabel(
                icon = Icons.Filled.SignalCellularAlt,
                text = "${latencyMs} ms",
                tint = latencyColor(latencyMs),
            )
        }

        Text(
            text = if (edition == ServerEdition.BEDROCK) "Bedrock" else "Java",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (online) StatusOnline else StatusOffline),
    )
}

private fun latencyColor(ms: Long) = when {
    ms < 100 -> LatencyGood
    ms < 300 -> LatencyOk
    else -> LatencyBad
}
