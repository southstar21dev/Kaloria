package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.network.PocketBaseClient
import dev.southstar21.kaloria.core.network.StaleRecordException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Where the engine keeps the per-collection pull cursors (the session store in the app). */
interface CursorStore {
    suspend fun cursor(collection: String): String?
    suspend fun setCursor(collection: String, value: String)
}

/** [rejected]: rows the server refused outright (e.g. failed validation); they stay dirty and are retried. */
data class SyncReport(val pushed: Int, val pulled: Int, val rejectedAsStale: Int, val rejected: Int = 0) {
    operator fun plus(other: SyncReport) = SyncReport(pushed + other.pushed, pulled + other.pulled, rejectedAsStale + other.rejectedAsStale, rejected + other.rejected)
}

/**
 * Two-way sync with last-write-wins on the client timestamp:
 * 1. push every dirty row (the server refuses ones older than what it has, with 409);
 * 2. pull everything changed since the last cursor and keep whichever side is newer.
 * Each row is marked clean only if it wasn't edited again while being pushed. A row the server
 * refuses for any other reason is skipped, so one bad row can't hold up the rest.
 */
class SyncEngine(
    private val client: PocketBaseClient,
    private val cursors: CursorStore,
    private val tables: List<SyncTable<*>>,
) {
    suspend fun sync(session: Session): SyncReport = tables.fold(SyncReport(0, 0, 0)) { report, table -> report + syncTable(session, table) }

    /** Whether any table still has rows to push, e.g. ones written while a sync was running. */
    suspend fun hasPending(): Boolean = tables.any { it.dirty().isNotEmpty() }

    private sealed interface Push {
        data object Pushed : Push
        data object Stale : Push
        data object Rejected : Push
    }

    private suspend fun <E> syncTable(session: Session, table: SyncTable<E>): SyncReport {
        val since = cursors.cursor(table.collection)
        // Before this table's first pull, most dirty rows have never been on the server.
        val likelyNew = since == null
        val outcomes = table.dirty().chunked(PARALLEL_PUSHES).flatMap { chunk ->
            coroutineScope {
                chunk.map { row -> async { row to push(session, table, row, likelyNew) } }.awaitAll()
            }
        }
        table.markClean(outcomes.filter { it.second == Push.Pushed }.map { it.first })

        val records = client.changesSince(session.serverUrl, session.token, table.collection, since)
        var pulled = 0
        if (records.isNotEmpty()) {
            val remote = records.mapNotNull(table::fromJson)
            val local = remote.map(table::id).chunked(MAX_IDS_PER_QUERY).flatMap { table.byIds(it) }.associateBy(table::id)
            val newer = remote.filter { r -> local[table.id(r)]?.let { table.updatedAt(r) > table.updatedAt(it) } ?: true }
            if (newer.isNotEmpty()) table.saveRemote(newer)
            pulled = newer.size
            records.last()["updated"]?.jsonPrimitive?.contentOrNull?.let { cursors.setCursor(table.collection, it) }
        }
        return SyncReport(
            pushed = outcomes.count { it.second == Push.Pushed },
            pulled = pulled,
            rejectedAsStale = outcomes.count { it.second == Push.Stale },
            rejected = outcomes.count { it.second == Push.Rejected },
        )
    }

    private suspend fun <E> push(session: Session, table: SyncTable<E>, row: E, likelyNew: Boolean): Push = try {
        client.upsert(session.serverUrl, session.token, table.collection, table.id(row), session.userId, table.toJson(row), likelyNew)
        Push.Pushed
    } catch (_: StaleRecordException) {
        // The server's copy is newer; the pull below brings it down and replaces ours.
        Push.Stale
    } catch (e: ClientRequestException) {
        // 400/404/422: this record can't be written as it is. 401 is handled by the client (auth).
        if (e.response.status.value in 400..499 && e.response.status != HttpStatusCode.TooManyRequests) {
            Push.Rejected
        } else {
            throw e
        }
    }

    private companion object {
        const val PARALLEL_PUSHES = 8

        /** Well under SQLite's bound-variable limit. */
        const val MAX_IDS_PER_QUERY = 500
    }
}

internal class SessionCursorStore(private val store: SessionStore) : CursorStore {
    override suspend fun cursor(collection: String) = store.cursor(collection)
    override suspend fun setCursor(collection: String, value: String) {
        store.setCursor(collection, value)
    }
}
