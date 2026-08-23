package com.mcserverstatus.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY id ASC")
    fun observeAll(): Flow<List<ServerEntry>>

    /** One-shot fetch for background work (the status-check worker), rather than a live Flow. */
    @Query("SELECT * FROM servers WHERE notifyOnStatusChange = 1")
    suspend fun getNotifyEnabled(): List<ServerEntry>

    /** The server the home screen widget shows, if one has been picked. */
    @Query("SELECT * FROM servers WHERE isFavorite = 1 LIMIT 1")
    suspend fun getFavorite(): ServerEntry?

    /** Only one server is ever favorited at a time. */
    @Transaction
    suspend fun setFavorite(id: Long) {
        clearFavorite()
        markFavorite(id)
    }

    @Query("UPDATE servers SET isFavorite = 0")
    suspend fun clearFavorite()

    @Query("UPDATE servers SET isFavorite = 1 WHERE id = :id")
    suspend fun markFavorite(id: Long)

    @Insert
    suspend fun insert(entry: ServerEntry): Long

    @Update
    suspend fun update(entry: ServerEntry)

    @Delete
    suspend fun delete(entry: ServerEntry)
}
