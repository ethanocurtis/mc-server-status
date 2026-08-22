package com.mcserverstatus.app.network

import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.data.ServerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks the right wire protocol for a [ServerEntry] and runs it off the main
 * thread. When the entry has no explicit port, resolves the address the
 * same way the vanilla client does: a DNS SRV lookup for Java Edition
 * (falling back to the standard default port if there's no record), or just
 * the standard default port for Bedrock (which has no SRV concept).
 */
object ServerPinger {

    const val DEFAULT_TIMEOUT_MS = 5_000

    suspend fun ping(entry: ServerEntry, timeoutMs: Int = DEFAULT_TIMEOUT_MS): ServerStatusResult =
        withContext(Dispatchers.IO) {
            when (entry.edition) {
                ServerEdition.JAVA -> pingJava(entry, timeoutMs)
                ServerEdition.BEDROCK -> {
                    val port = entry.port ?: ServerEntry.DEFAULT_BEDROCK_PORT
                    BedrockServerPinger.ping(entry.host, port, timeoutMs)
                }
            }
        }

    private fun pingJava(entry: ServerEntry, timeoutMs: Int): ServerStatusResult {
        if (entry.port != null) {
            return JavaServerPinger.ping(entry.host, entry.port, timeoutMs)
        }

        val srv = DnsSrvResolver.resolve(entry.host)
        val (targetHost, targetPort) = if (srv != null) {
            srv.target.removeSuffix(".") to srv.port
        } else {
            entry.host to ServerEntry.DEFAULT_JAVA_PORT
        }
        val result = JavaServerPinger.ping(targetHost, targetPort, timeoutMs)
        return if (srv != null) result.copy(resolvedVia = srv) else result
    }
}
