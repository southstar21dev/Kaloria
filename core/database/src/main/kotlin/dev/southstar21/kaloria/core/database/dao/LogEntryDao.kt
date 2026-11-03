package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.southstar21.kaloria.core.database.model.LogEntryEntity
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

    @Query("UPDATE log_entries SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM log_entries WHERE dirty = 1")
    override suspend fun dirty(): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<LogEntryEntity>

    @Query("UPDATE log_entries SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}
