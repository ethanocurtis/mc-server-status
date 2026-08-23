package com.mcserverstatus.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY id ASC")
    fun observeAll(): Flow<List<ServerEntry>>

    /** One-shot fetch for background work (the status-check worker), rather than a live Flow. */
    @Query("SELECT * FROM servers WHERE notifyOnStatusChange = 1")
    suspend fun getNotifyEnabled(): List<ServerEntry>

    @Insert
    suspend fun insert(entry: ServerEntry): Long

    @Update
    suspend fun update(entry: ServerEntry)

    @Delete
    suspend fun delete(entry: ServerEntry)
}
