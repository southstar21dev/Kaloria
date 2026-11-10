package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import dev.southstar21.kaloria.core.database.model.FoodEntity
import dev.southstar21.kaloria.core.database.model.nextUpdatedAt
import dev.southstar21.kaloria.core.model.Serving
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface FoodDao : SyncDao<FoodEntity> {
    /** [search] with the LIKE [pattern] already built; [query] is still matched exactly against barcodes. */
    @Query(
        """
        SELECT foods.* FROM foods
        WHERE foods.deleted = 0
          AND (:query = '' OR foods.name LIKE :pattern ESCAPE '\' OR foods.brand LIKE :pattern ESCAPE '\'
               OR foods.barcode = :query)
        ORDER BY foods.favorite DESC,
          -- Per matching food, one seek in the (foodId, deleted, loggedAt) index, instead of grouping
          -- the whole diary on every keystroke.
          (SELECT MAX(loggedAt) FROM log_entries WHERE foodId = foods.id AND deleted = 0) DESC,
          foods.name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    fun searchLike(query: String, pattern: String, limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode AND deleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE source = :source AND sourceRef = :sourceRef AND deleted = 0 LIMIT 1")
    suspend fun bySourceRef(source: String, sourceRef: String): FoodEntity?

    /**
     * Saves a local edit, stamped with its [FoodEntity.updatedAt] or just after the stored copy's,
     * whichever is later (see [nextUpdatedAt]). Returns the row as saved.
     */
    @Transaction
    suspend fun save(entity: FoodEntity): FoodEntity = entity.copy(updatedAt = nextUpdatedAt(entity.updatedAt, get(entity.id)?.updatedAt)).also { upsertAll(listOf(it)) }

    /**
     * Replaces only the servings, so a sync or edit that landed since the food was read isn't
     * overwritten with that older copy. Returns how many live foods were updated (0 or 1).
     */
    @Query("UPDATE foods SET servings = :servings, dirty = 1, updatedAt = MAX(:now, updatedAt + 1) WHERE id = :id AND deleted = 0")
    suspend fun updateServings(id: String, servings: List<Serving>, now: Long): Int

    // Never stamped earlier than the row's current version, for the same reason as [save].
    @Query("UPDATE foods SET deleted = 1, dirty = 1, updatedAt = MAX(:now, updatedAt + 1) WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM foods WHERE dirty = 1")
    override suspend fun dirty(): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id IN (:ids)")
    override suspend fun byIds(ids: List<String>): List<FoodEntity>

    @Query("UPDATE foods SET dirty = 0 WHERE id = :id AND updatedAt = :pushedUpdatedAt")
    override suspend fun markClean(id: String, pushedUpdatedAt: Long)
}

/**
 * Own foods matching [query], favorites first, then most recently logged. `%` and `_` in [query]
 * are matched literally. SQLite's LIKE only ignores case for ASCII, so a query with other letters
 * ("äpfel") matches them as any one character in SQL, and the exact case-insensitive match is
 * done here.
 */
fun FoodDao.search(query: String, limit: Int = 200): Flow<List<FoodEntity>> {
    val ascii = query.all { it.code < ASCII_END }
    val pattern = buildString {
        append('%')
        query.forEach { c ->
            when {
                c == '%' || c == '_' || c == LIKE_ESCAPE -> append(LIKE_ESCAPE).append(c)

                c.code < ASCII_END -> append(c)

                // Skip the low half of a surrogate pair: the pair is one character to SQLite.
                c.isLowSurrogate() -> Unit

                else -> append('_')
            }
        }
        append('%')
    }
    if (ascii) return searchLike(query, pattern, limit)
    return searchLike(query, pattern, limit * NON_ASCII_HEADROOM).map { foods ->
        foods.filter { it.name.contains(query, ignoreCase = true) || it.brand?.contains(query, ignoreCase = true) == true || it.barcode == query }.take(limit)
    }
}

private const val LIKE_ESCAPE = '\\'
private const val ASCII_END = 128

/** Non-ASCII letters match any character in SQL, so [FoodDao.search] fetches extra before its exact filter. */
private const val NON_ASCII_HEADROOM = 4
