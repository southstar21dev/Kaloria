package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.southstar21.kaloria.core.database.model.GoalsEntity
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao : SyncDao<WeightEntryEntity> {
    @Query("SELECT * FROM weight_entries WHERE deleted = 0 AND loggedAt >= :from AND loggedAt < :to ORDER BY loggedAt")
    fun between(from: Long, to: Long): Flow<List<WeightEntryEntity>>

    /** Every row since [from], deleted ones included: Health Connect sync needs the tombstones. */
    @Query("SELECT * FROM weight_entries WHERE loggedAt >= :from")
    suspend fun allSince(from: Long): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries WHERE deleted = 0 ORDER BY loggedAt DESC LIMIT 1")
    fun latest(): Flow<WeightEntryEntity?>

    @Upsert
    suspend fun upsert(entity: WeightEntryEntity)

    @Query("UPDATE weight_entries SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE weight_entries SET deleted = 1, dirty = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteAll(ids: List<String>, now: Long)

    /** Rows from before imports had valid ids: `hc:<Health Connect id>`. They never reached the server. */
    @Query("SELECT * FROM weight_entries WHERE id LIKE 'hc:%'")
    suspend fun legacyImports(): List<WeightEntryEntity>

    @Query("DELETE FROM weight_entries WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<String>)

    /** Re-keys legacy imports in one go, so a crash can't leave both copies or neither. */
    @Transaction
    suspend fun replace(old: List<String>, new: List<WeightEntryEntity>) {
        hardDelete(old)
        upsertAll(new)
    }

    /** Applies one Health Connect import round at once, so observers see one change, not one per row. */
    @Transaction
    suspend fun applyImport(upserts: List<WeightEntryEntity>, deletedIds: List<String>, now: Long) {
        if (upserts.isNotEmpty()) upsertAll(upserts)
        if (deletedIds.isNotEmpty()) softDeleteAll(deletedIds, now)
    }

    @Query("SELECT * FROM weight_entries WHERE dirty = 1")
    override suspend fun dirty(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<WeightEntryEntity>

    @Query("UPDATE weight_entries SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}

@Dao
interface GoalsDao : SyncDao<GoalsEntity> {
    /** The live goals row. After a sync from another device there can briefly be two; newest wins. */
    @Query("SELECT * FROM goals WHERE deleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    fun observe(): Flow<GoalsEntity?>

    @Query("SELECT * FROM goals WHERE deleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun current(): GoalsEntity?

    @Upsert
    suspend fun upsert(entity: GoalsEntity)

    /** Saves over the live row, or creates it; atomic, so two quick saves can't make two rows. */
    @Transaction
    suspend fun saveCurrent(newId: String, build: (id: String) -> GoalsEntity) {
        upsert(build(current()?.id ?: newId))
    }

    @Query("SELECT * FROM goals WHERE dirty = 1")
    override suspend fun dirty(): List<GoalsEntity>

    @Query("SELECT * FROM goals WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<GoalsEntity>

    @Query("UPDATE goals SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}
