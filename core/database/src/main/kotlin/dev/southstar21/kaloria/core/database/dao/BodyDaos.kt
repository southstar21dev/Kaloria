package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.southstar21.kaloria.core.database.model.GoalsEntity
import dev.southstar21.kaloria.core.database.model.WaterEntryEntity
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao : SyncDao<WaterEntryEntity> {
    @Query("SELECT * FROM water_entries WHERE deleted = 0 AND loggedAt >= :from AND loggedAt < :to ORDER BY loggedAt")
    fun between(from: Long, to: Long): Flow<List<WaterEntryEntity>>

    @Upsert
    suspend fun upsert(entity: WaterEntryEntity)

    @Query("UPDATE water_entries SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM water_entries WHERE dirty = 1")
    override suspend fun dirty(): List<WaterEntryEntity>

    @Query("SELECT * FROM water_entries WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<WaterEntryEntity>

    @Query("UPDATE water_entries SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}

@Dao
interface WeightDao : SyncDao<WeightEntryEntity> {
    @Query("SELECT * FROM weight_entries WHERE deleted = 0 AND loggedAt >= :from AND loggedAt < :to ORDER BY loggedAt")
    fun between(from: Long, to: Long): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE deleted = 0 ORDER BY loggedAt DESC LIMIT 1")
    fun latest(): Flow<WeightEntryEntity?>

    @Upsert
    suspend fun upsert(entity: WeightEntryEntity)

    @Query("UPDATE weight_entries SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

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

    @Query("SELECT * FROM goals WHERE dirty = 1")
    override suspend fun dirty(): List<GoalsEntity>

    @Query("SELECT * FROM goals WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<GoalsEntity>

    @Query("UPDATE goals SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}
