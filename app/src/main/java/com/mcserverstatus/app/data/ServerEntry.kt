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
    val port: Int,
    val edition: ServerEdition,
) {
    /** Display name: the user's nickname if set, otherwise the host. */
    val displayName: String get() = name.ifBlank { host }

    companion object {
        const val DEFAULT_JAVA_PORT = 25565
        const val DEFAULT_BEDROCK_PORT = 19132
    }
}
