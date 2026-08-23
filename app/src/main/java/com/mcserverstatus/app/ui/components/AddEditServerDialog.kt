package com.mcserverstatus.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.data.ServerEntry
import com.mcserverstatus.app.network.EditionDetector
import kotlinx.coroutines.launch

private enum class EditionChoice { AUTO, JAVA, BEDROCK }

private fun EditionChoice.toServerEdition(): ServerEdition? = when (this) {
    EditionChoice.AUTO -> null
    EditionChoice.JAVA -> ServerEdition.JAVA
    EditionChoice.BEDROCK -> ServerEdition.BEDROCK
}

/**
 * Add/edit form for a server entry. Pass [existing] to pre-fill for editing;
 * leave it null to add a new server. When "Auto" edition is selected, saving
 * probes the address for both protocols before calling [onSave].
 */
@Composable
fun AddEditServerDialog(
    existing: ServerEntry?,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, port: Int?, edition: ServerEdition, notifyOnStatusChange: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var editionChoice by remember {
        mutableStateOf(
            when (existing?.edition) {
                ServerEdition.JAVA -> EditionChoice.JAVA
                ServerEdition.BEDROCK -> EditionChoice.BEDROCK
                null -> EditionChoice.AUTO
            },
        )
    }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "") }
    var notifyOnStatusChange by remember { mutableStateOf(existing?.notifyOnStatusChange ?: false) }
    var hostError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var detectFailed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* if denied, NotificationHelper simply no-ops when it can't post - nothing to handle here */ }

    val portHint = when (editionChoice) {
        EditionChoice.BEDROCK -> "Optional - defaults to ${ServerEntry.DEFAULT_BEDROCK_PORT}"
        EditionChoice.JAVA -> "Optional - auto-detected (SRV), or ${ServerEntry.DEFAULT_JAVA_PORT}"
        EditionChoice.AUTO -> "Optional - leave blank unless you know the exact port"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editionChoice == EditionChoice.AUTO,
                        onClick = { editionChoice = EditionChoice.AUTO; detectFailed = false },
                        label = { Text("Auto-detect") },
                    )
                    FilterChip(
                        selected = editionChoice == EditionChoice.JAVA,
                        onClick = { editionChoice = EditionChoice.JAVA; detectFailed = false },
                        label = { Text("Java") },
                    )
                    FilterChip(
                        selected = editionChoice == EditionChoice.BEDROCK,
                        onClick = { editionChoice = EditionChoice.BEDROCK; detectFailed = false },
                        label = { Text("Bedrock") },
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nickname (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; hostError = false },
                    label = { Text("Server address") },
                    placeholder = { Text("play.example.com") },
                    singleLine = true,
                    isError = hostError,
                    supportingText = { if (hostError) Text("Enter a server address") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit); portError = false },
                    label = { Text("Port") },
                    singleLine = true,
                    isError = portError,
                    supportingText = { Text(if (portError) "Enter a port between 1 and 65535, or leave it blank" else portHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (detectFailed) {
                    Text(
                        "Couldn't reach that address to detect its edition. Check it, or pick Java/Bedrock manually.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notify on status change", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Alerts you when this server goes offline or comes back online",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = notifyOnStatusChange,
                        onCheckedChange = { checked ->
                            notifyOnStatusChange = checked
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !detecting,
                onClick = {
                    val trimmedHost = host.trim()
                    val trimmedPort = port.trim()
                    val portValue = trimmedPort.toIntOrNull()
                    hostError = trimmedHost.isEmpty()
                    portError = trimmedPort.isNotEmpty() && (portValue == null || portValue !in 1..65535)
                    if (hostError || portError) return@TextButton

                    val chosenEdition = editionChoice.toServerEdition()
                    if (chosenEdition != null) {
                        onSave(name.trim(), trimmedHost, portValue, chosenEdition, notifyOnStatusChange)
                    } else {
                        detecting = true
                        detectFailed = false
                        coroutineScope.launch {
                            val detected = EditionDetector.detect(trimmedHost, portValue)
                            detecting = false
                            if (detected != null) {
                                onSave(name.trim(), trimmedHost, portValue, detected, notifyOnStatusChange)
                            } else {
                                detectFailed = true
                            }
                        }
                    }
                },
            ) {
                if (detecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (existing == null) "Add" else "Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !detecting) { Text("Cancel") }
        },
    )
}
