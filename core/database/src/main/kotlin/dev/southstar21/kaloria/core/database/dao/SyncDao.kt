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

    /**
     * Writes the [remote] rows that are new here or newer than the local copy, and returns how many.
     * The comparison and the write are one transaction, so a local edit saved in between can't be
     * overwritten by the older remote copy and then lost.
     */
    @Transaction
    suspend fun upsertIfNewer(remote: List<E>, id: (E) -> String, updatedAt: (E) -> Long): Int {
        // Well under SQLite's bound-variable limit.
        val local = remote.map(id).chunked(500).flatMap { byIds(it) }.associateBy(id)
        val newer = remote.filter { r -> local[id(r)]?.let { updatedAt(r) > updatedAt(it) } ?: true }
        if (newer.isNotEmpty()) upsertAll(newer)
        return newer.size
    }
}
