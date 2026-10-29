package dev.southstar21.kaloria.core.database.dao

import androidx.room.Upsert

/** The operations the sync engine needs on every table. */
interface SyncDao<E> {
    suspend fun dirty(): List<E>

    suspend fun byIds(ids: List<String>): List<E>

    /** Clears the row's dirty flag unless they were edited again after [pushedUpdatedAt] was read. */
    suspend fun markClean(id: String, pushedUpdatedAt: Long)

    @Upsert
    suspend fun upsertAll(entities: List<E>)
}
