package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.southstar21.kaloria.core.database.model.LogEntryEntity
import dev.southstar21.kaloria.core.database.model.nextUpdatedAt
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao : SyncDao<LogEntryEntity> {
    /** Entries with `from <= loggedAt < to`, oldest first (the timeline order). */
    @Query("SELECT * FROM log_entries WHERE deleted = 0 AND loggedAt >= :from AND loggedAt < :to ORDER BY loggedAt")
    fun between(from: Long, to: Long): Flow<List<LogEntryEntity>>

    /**
     * The quarter-hours (epoch millis / 15 min) with at least one live entry, for working out which
     * days have any (the streak). Every time zone's offset is a whole number of quarter-hours, so a
     * quarter-hour never straddles local midnight, and this is a few rows per day instead of one per entry.
     */
    @Query("SELECT DISTINCT loggedAt / 900000 FROM log_entries WHERE deleted = 0")
    fun loggedQuarterHours(): Flow<List<Long>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun get(id: String): LogEntryEntity?

    @Upsert
    suspend fun upsert(entity: LogEntryEntity)

    /**
     * Saves a local edit, stamped with its [LogEntryEntity.updatedAt] or just after the stored copy's,
     * whichever is later (see [nextUpdatedAt]). Returns the row as saved.
     */
    @Transaction
    suspend fun save(entity: LogEntryEntity): LogEntryEntity = entity.copy(updatedAt = nextUpdatedAt(entity.updatedAt, get(entity.id)?.updatedAt)).also { upsert(it) }

    // Never stamped earlier than the row's current version, for the same reason as [save].
    @Query("UPDATE log_entries SET deleted = 1, dirty = 1, updatedAt = MAX(:now, updatedAt + 1) WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM log_entries WHERE dirty = 1")
    override suspend fun dirty(): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<LogEntryEntity>

    @Query("UPDATE log_entries SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}
