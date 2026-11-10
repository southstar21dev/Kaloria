package dev.southstar21.kaloria.core.sync

import kotlinx.serialization.json.JsonObject

/** One synced table as the engine sees it: rows with an id, a client timestamp and a dirty flag. */
interface SyncTable<E> {
    /** PocketBase collection name. */
    val collection: String

    suspend fun dirty(): List<E>

    /**
     * Stores the rows from the server that are new here or newer than the local copy, clean, and
     * returns how many. Comparing and writing is atomic, so a local edit can't slip in between.
     */
    suspend fun saveIfNewer(rows: List<E>): Int

    /** Clears the dirty flag of each pushed row that wasn't edited again meanwhile, in one go. */
    suspend fun markClean(rows: List<E>)

    fun id(row: E): String
    fun updatedAt(row: E): Long

    /** Record fields for PocketBase, without id/owner (the client adds those). */
    fun toJson(row: E): JsonObject

    /** A server record as a clean local row; null if it can't be read (skipped, not fatal). */
    fun fromJson(json: JsonObject): E?
}
