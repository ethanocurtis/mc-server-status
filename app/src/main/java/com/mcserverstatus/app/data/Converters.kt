package com.mcserverstatus.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEdition(edition: ServerEdition): String = edition.name

    @TypeConverter
    fun toEdition(value: String): ServerEdition =
        runCatching { ServerEdition.valueOf(value) }.getOrDefault(ServerEdition.JAVA)
}
