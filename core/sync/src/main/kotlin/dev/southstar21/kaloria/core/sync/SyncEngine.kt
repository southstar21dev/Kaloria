package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.network.PocketBaseClient
import dev.southstar21.kaloria.core.network.StaleRecordException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Where the engine keeps the per-collection pull cursors (the session store in the app). */
interface CursorStore {
    suspend fun cursor(collection: String): String?
    suspend fun setCursor(collection: String, value: String)
}

data class SyncReport(val pushed: Int, val pulled: Int, val rejectedAsStale: Int)

/**
 * Two-way sync with last-write-wins on the client timestamp:
 * 1. push every dirty row (the server refuses ones older than what it has, with 409);
 * 2. pull everything changed since the last cursor and keep whichever side is newer.
 * Each row is marked clean only if it wasn't edited again while being pushed.
 */
class SyncEngine(
    private val client: PocketBaseClient,
    private val cursors: CursorStore,
    private val tables: List<SyncTable<*>>,
) {
    suspend fun sync(session: Session): SyncReport {
        var pushed = 0
        var pulled = 0
        var stale = 0
        for (table in tables) {
            val result = syncTable(session, table)
            pushed += result.pushed
            pulled += result.pulled
            stale += result.rejectedAsStale
        }
        return SyncReport(pushed, pulled, stale)
    }

    private suspend fun <E> syncTable(session: Session, table: SyncTable<E>): SyncReport {
        var pushed = 0
        var stale = 0
        for (row in table.dirty()) {
            try {
                client.upsert(session.serverUrl, session.token, table.collection, table.id(row), session.userId, table.toJson(row))
                table.markClean(row)
                pushed++
            } catch (_: StaleRecordException) {
                // The server's copy is newer; the pull below brings it down and replaces ours.
                stale++
            }
        }

        val since = cursors.cursor(table.collection)
        val records = client.changesSince(session.serverUrl, session.token, table.collection, since)
        if (records.isEmpty()) return SyncReport(pushed, 0, stale)

        val remote = records.mapNotNull(table::fromJson)
        val local = table.byIds(remote.map(table::id)).associateBy(table::id)
        val newer = remote.filter { r -> local[table.id(r)]?.let { table.updatedAt(r) > table.updatedAt(it) } ?: true }
        if (newer.isNotEmpty()) table.saveRemote(newer)

        records.last()["updated"]?.jsonPrimitive?.contentOrNull?.let { cursors.setCursor(table.collection, it) }
        return SyncReport(pushed, newer.size, stale)
    }
}

internal class SessionCursorStore(private val store: SessionStore) : CursorStore {
    override suspend fun cursor(collection: String) = store.cursor(collection)
    override suspend fun setCursor(collection: String, value: String) {
        store.setCursor(collection, value)
    }
}

