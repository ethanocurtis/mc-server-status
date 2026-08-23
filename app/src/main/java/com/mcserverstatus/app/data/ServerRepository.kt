package com.mcserverstatus.app.data

import kotlinx.coroutines.flow.Flow

class ServerRepository(private val dao: ServerDao) {

    val servers: Flow<List<ServerEntry>> = dao.observeAll()

    suspend fun add(entry: ServerEntry): Long = dao.insert(entry)

    suspend fun update(entry: ServerEntry) = dao.update(entry)

    suspend fun delete(entry: ServerEntry) = dao.delete(entry)

    suspend fun setFavorite(id: Long) = dao.setFavorite(id)
}
