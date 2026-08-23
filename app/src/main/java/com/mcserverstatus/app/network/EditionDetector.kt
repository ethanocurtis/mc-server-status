package com.mcserverstatus.app.network

import com.mcserverstatus.app.data.ServerEdition
import com.mcserverstatus.app.data.ServerEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Best-effort edition detection for the "auto" option when adding a server:
 * probes Java and Bedrock concurrently and reports whichever one actually
 * answered, so the user doesn't need to already know which edition a server
 * runs. Returns null if neither responded (e.g. the server is offline right
 * now) - the caller should fall back to a sane default and let the user
 * correct it later if needed.
 */
object EditionDetector {

    suspend fun detect(host: String, port: Int?, timeoutMs: Int = 2_500): ServerEdition? =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val javaOk = async { probeJava(host, port, timeoutMs) }
                val bedrockOk = async { probeBedrock(host, port, timeoutMs) }
                when {
                    javaOk.await() -> ServerEdition.JAVA
                    bedrockOk.await() -> ServerEdition.BEDROCK
                    else -> null
                }
            }
        }

    private fun probeJava(host: String, port: Int?, timeoutMs: Int): Boolean {
        val targetPort = port ?: (DnsSrvResolver.resolve(host)?.port ?: ServerEntry.DEFAULT_JAVA_PORT)
        return JavaServerPinger.ping(host, targetPort, timeoutMs).success
    }

    private fun probeBedrock(host: String, port: Int?, timeoutMs: Int): Boolean {
        val targetPort = port ?: ServerEntry.DEFAULT_BEDROCK_PORT
        return BedrockServerPinger.ping(host, targetPort, timeoutMs).success
    }
}
