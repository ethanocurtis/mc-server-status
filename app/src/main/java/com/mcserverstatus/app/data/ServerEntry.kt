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
     * Last *confirmed* online/offline state observed by the background worker, used to detect
     * a change rather than notifying on every check. Null until the worker has confirmed this
     * server's state at least once.
     */
    val lastKnownOnline: Boolean? = null,
    /**
     * A reading that disagreed with [lastKnownOnline] on the most recent check, but hasn't been
     * confirmed by a second consecutive check yet - see [com.mcserverstatus.app.work.StatusCheckWorker].
     * This debounce exists because a single failed ping is ambiguous: it's just as likely to be
     * the *phone's* network having a bad moment (weak signal, a Wi-Fi/cellular handoff, one DNS
     * hiccup) as the server actually going down, and firing a notification on every blip made
     * the feature useless. Requiring the same reading twice in a row (~15-30 min apart) before
     * treating it as real filters that out, at the cost of one extra check's worth of delay.
     */
    val pendingStatusChange: Boolean? = null,
    /** At most one server is favorited at a time - it's the one the home screen widget shows. */
    val isFavorite: Boolean = false,
    /**
     * A cached snapshot of the favorite's last real ping result, so the widget has something
     * to show without pinging on every render. Populated only for the favorited server (by
     * [com.mcserverstatus.app.widget.WidgetUpdater]); null on every other row, and null on the
     * favorite itself until it's been checked once, or whenever it's currently offline.
     */
    val lastCheckedAt: Long? = null,
    /**
     * Separate from [lastKnownOnline] on purpose: this is the widget's own immediate,
     * undebounced reading. If the two shared a field, a server that's both favorited and
     * notify-enabled would have WidgetUpdater's unconditional overwrite silently undo
     * [pendingStatusChange]'s debounce every cycle.
     */
    val widgetOnline: Boolean? = null,
    val lastKnownPlayersOnline: Int? = null,
    val lastKnownPlayersMax: Int? = null,
    val lastKnownLatencyMs: Long? = null,
    val lastKnownMotd: String? = null,
    val lastKnownFaviconBase64: String? = null,
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
