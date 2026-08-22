package com.mcserverstatus.app.network

import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.data.ServerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picks the right wire protocol for a [ServerEntry] and runs it off the main thread. */
object ServerPinger {

    const val DEFAULT_TIMEOUT_MS = 5_000

    suspend fun ping(entry: ServerEntry, timeoutMs: Int = DEFAULT_TIMEOUT_MS): ServerStatusResult =
        withContext(Dispatchers.IO) {
            when (entry.edition) {
                ServerEdition.JAVA -> JavaServerPinger.ping(entry.host, entry.port, timeoutMs)
                ServerEdition.BEDROCK -> BedrockServerPinger.ping(entry.host, entry.port, timeoutMs)
            }
        }
}
