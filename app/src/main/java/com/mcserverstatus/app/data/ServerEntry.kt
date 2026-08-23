package com.mcserverstatus.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ServerEdition {
    JAVA,
    BEDROCK,
}

@Entity(tableName = "servers")
data class ServerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    /**
     * Explicit port, or null to auto-detect. For Java Edition, null means
     * "resolve via the `_minecraft._tcp` DNS SRV record, falling back to
     * [DEFAULT_JAVA_PORT]" - exactly how the vanilla client treats a bare
     * hostname. For Bedrock (no SRV support), null just means
     * [DEFAULT_BEDROCK_PORT].
     */
    val port: Int?,
    val edition: ServerEdition,
    /** Whether the background status-check worker should notify on an online/offline flip. */
    val notifyOnStatusChange: Boolean = false,
    /**
     * Last online/offline state observed by the background worker, used to detect a *change*
     * rather than notifying on every check. Null until the worker has checked this server once.
     */
    val lastKnownOnline: Boolean? = null,
) {
    /** Display name: the user's nickname if set, otherwise the host. */
    val displayName: String get() = name.ifBlank { host }

    /** What the user actually typed/would type to connect - omits the port when it's auto-detected. */
    val addressLabel: String get() = if (port != null) "$host:$port" else host

    companion object {
        const val DEFAULT_JAVA_PORT = 25565
        const val DEFAULT_BEDROCK_PORT = 19132
    }
}
