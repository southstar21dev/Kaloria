package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.southstar21.kaloria.core.database.model.FoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao : SyncDao<FoodEntity> {
    /** Own foods matching [query], favorites first, then most recently logged. */
    @Query(
        """
        SELECT foods.* FROM foods
        LEFT JOIN (
            SELECT foodId, MAX(loggedAt) AS lastLogged FROM log_entries WHERE deleted = 0 GROUP BY foodId
        ) recent ON recent.foodId = foods.id
        WHERE foods.deleted = 0
          AND (:query = '' OR foods.name LIKE '%' || :query || '%' OR foods.brand LIKE '%' || :query || '%'
               OR foods.barcode = :query)
        ORDER BY foods.favorite DESC, recent.lastLogged DESC, foods.name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int = 200): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode AND deleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE source = :source AND sourceRef = :sourceRef AND deleted = 0 LIMIT 1")
    suspend fun bySourceRef(source: String, sourceRef: String): FoodEntity?

    @Upsert
    suspend fun upsert(entity: FoodEntity)

    @Query("UPDATE foods SET deleted = 1, dirty = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM foods WHERE dirty = 1")
    override suspend fun dirty(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<FoodEntity>

    @Query("UPDATE foods SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}
