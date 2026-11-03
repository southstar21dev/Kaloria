package dev.southstar21.kaloria.core.database.dao

import androidx.room.Transaction
import androidx.room.Upsert

/** The operations the sync engine needs on every table. */
interface SyncDao<E> {
    suspend fun dirty(): List<E>

    suspend fun byIds(ids: List<String>): List<E>

    /** Clears the row's dirty flag unless they were edited again after [pushedUpdatedAt] was read. */
    suspend fun markClean(id: String, pushedUpdatedAt: Long)

    /** [markClean] for many rows (id to pushed updatedAt) in one transaction. */
    @Transaction
    suspend fun markCleanAll(pushed: Map<String, Long>) {
        pushed.forEach { (id, updatedAt) -> markClean(id, updatedAt) }
    }

    @Upsert
    suspend fun upsertAll(entities: List<E>)
}
