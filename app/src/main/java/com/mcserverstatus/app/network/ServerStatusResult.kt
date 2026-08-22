package com.mcserverstatus.app.network

/**
 * Outcome of pinging a single server. Used for both Java and Bedrock edition
 * results; fields that don't apply to a given edition (e.g. [faviconBase64]
 * for Bedrock) are simply left null/empty.
 */
data class ServerStatusResult(
    val success: Boolean,
    val latencyMs: Long? = null,
    val motd: List<MotdSegment> = emptyList(),
    val motdLine2: List<MotdSegment> = emptyList(),
    val playersOnline: Int? = null,
    val playersMax: Int? = null,
    val playerSample: List<String> = emptyList(),
    val versionName: String? = null,
    val protocolVersion: Int? = null,
    val faviconBase64: String? = null,
    val gamemode: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        fun failure(message: String): ServerStatusResult =
            ServerStatusResult(success = false, errorMessage = message)
    }
}
