package dev.southstar21.kaloria.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** All synced tables at once, for when the account the local data belongs to changes. */
@Suppress("TooManyFunctions") // Room runs one statement per query method, so each table needs its own
@Dao
interface LocalDataDao {
    /** Whether anything not deleted is stored. */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM foods WHERE deleted = 0)
            OR EXISTS(SELECT 1 FROM log_entries WHERE deleted = 0)
            OR EXISTS(SELECT 1 FROM weight_entries WHERE deleted = 0)
            OR EXISTS(SELECT 1 FROM goals WHERE deleted = 0)
        """,
    )
    suspend fun hasData(): Boolean

    /** Whether any row has been synced with some account (a clean row came from or went to a server). */
    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM foods WHERE dirty = 0)
            OR EXISTS(SELECT 1 FROM log_entries WHERE dirty = 0)
            OR EXISTS(SELECT 1 FROM weight_entries WHERE dirty = 0)
            OR EXISTS(SELECT 1 FROM goals WHERE dirty = 0)
        """,
    )
    suspend fun hasSyncedRows(): Boolean

    @Query("UPDATE foods SET dirty = 1")
    suspend fun markFoodsDirty()

    @Query("UPDATE log_entries SET dirty = 1")
    suspend fun markLogEntriesDirty()

    @Query("UPDATE weight_entries SET dirty = 1")
    suspend fun markWeightsDirty()

    @Query("UPDATE goals SET dirty = 1")
    suspend fun markGoalsDirty()

    /** Queues every row, tombstones included, to be uploaded again (to a new or reset server). */
    @Transaction
    suspend fun markAllDirty() {
        markFoodsDirty()
        markLogEntriesDirty()
        markWeightsDirty()
        markGoalsDirty()
    }

    @Query("DELETE FROM foods")
    suspend fun deleteFoods()

    @Query("DELETE FROM log_entries")
    suspend fun deleteLogEntries()

    @Query("DELETE FROM weight_entries")
    suspend fun deleteWeights()

    @Query("DELETE FROM goals")
    suspend fun deleteGoals()

    /** Empties every synced table, so the next sync brings down another account's data alone. */
    @Transaction
    suspend fun deleteAll() {
        deleteFoods()
        deleteLogEntries()
        deleteWeights()
        deleteGoals()
    }
}
