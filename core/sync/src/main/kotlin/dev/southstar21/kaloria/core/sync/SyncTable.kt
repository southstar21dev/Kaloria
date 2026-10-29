package dev.southstar21.kaloria.core.sync

import kotlinx.serialization.json.JsonObject

/** One synced table as the engine sees it: rows with an id, a client timestamp and a dirty flag. */
interface SyncTable<E> {
    /** PocketBase collection name. */
    val collection: String

    suspend fun dirty(): List<E>
    suspend fun byIds(ids: List<String>): List<E>

    /** Writes rows that came from the server; they are stored clean. */
    suspend fun saveRemote(rows: List<E>)
    suspend fun markClean(row: E)

    fun id(row: E): String
    fun updatedAt(row: E): Long

    /** Record fields for PocketBase, without id/owner (the client adds those). */
    fun toJson(row: E): JsonObject

    /** A server record as a clean local row; null if it can't be read (skipped, not fatal). */
    fun fromJson(json: JsonObject): E?
}
