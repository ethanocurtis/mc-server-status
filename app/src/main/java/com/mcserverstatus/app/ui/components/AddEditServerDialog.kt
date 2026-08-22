package com.mcserverstatus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.data.ServerEntry

/**
 * Add/edit form for a server entry. Pass [existing] to pre-fill for editing;
 * leave it null to add a new server.
 */
@Composable
fun AddEditServerDialog(
    existing: ServerEntry?,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, port: Int, edition: ServerEdition) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var edition by remember { mutableStateOf(existing?.edition ?: ServerEdition.JAVA) }
    var port by remember {
        mutableStateOf((existing?.port ?: ServerEntry.DEFAULT_JAVA_PORT).toString())
    }
    var portTouched by remember { mutableStateOf(existing != null) }
    var hostError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }

    fun defaultPortFor(e: ServerEdition) =
        if (e == ServerEdition.BEDROCK) ServerEntry.DEFAULT_BEDROCK_PORT else ServerEntry.DEFAULT_JAVA_PORT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add server" else "Edit server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = edition == ServerEdition.JAVA,
                        onClick = {
                            edition = ServerEdition.JAVA
                            if (!portTouched) port = defaultPortFor(ServerEdition.JAVA).toString()
                        },
                        label = { Text("Java") },
                    )
                    FilterChip(
                        selected = edition == ServerEdition.BEDROCK,
                        onClick = {
                            edition = ServerEdition.BEDROCK
                            if (!portTouched) port = defaultPortFor(ServerEdition.BEDROCK).toString()
                        },
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
                    onValueChange = { port = it.filter(Char::isDigit); portTouched = true; portError = false },
                    label = { Text("Port") },
                    singleLine = true,
                    isError = portError,
                    supportingText = { if (portError) Text("Enter a port between 1 and 65535") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedHost = host.trim()
                val portValue = port.toIntOrNull()
                hostError = trimmedHost.isEmpty()
                portError = portValue == null || portValue !in 1..65535
                if (!hostError && !portError) {
                    onSave(name.trim(), trimmedHost, portValue!!, edition)
                }
            }) {
                Text(if (existing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
