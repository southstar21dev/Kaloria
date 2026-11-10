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

/**
 * [rejected]: rows the server refused outright (e.g. failed validation); they stay dirty and are
 * retried next run. [rejectedAsStale]: local edits the server refused as older than its copy, which
 * then replaced them.
 */
data class SyncReport(val pushed: Int, val pulled: Int, val rejectedAsStale: Int, val rejected: Int = 0) {
    operator fun plus(other: SyncReport) = SyncReport(pushed + other.pushed, pulled + other.pulled, rejectedAsStale + other.rejectedAsStale, rejected + other.rejected)

    companion object {
        val NONE = SyncReport(0, 0, 0)
    }
}

/**
 * Two-way sync with last-write-wins on the client timestamp:
 * 1. push every dirty row (the server refuses ones older than what it has, with 409);
 * 2. pull everything changed since the last cursor and keep whichever side is newer;
 * 3. fetch the server's copy of any refused-as-older row the pull didn't bring.
 * Each row is marked clean only if it wasn't edited again while being pushed. A row the server
 * refuses for any other reason is skipped, so one bad row can't hold up the rest.
 */
class SyncEngine(
    private val client: PocketBaseClient,
    private val cursors: CursorStore,
    private val tables: List<SyncTable<*>>,
) {
    /** One pass over every table. */
    suspend fun sync(session: Session): SyncReport = syncRound(session, mutableSetOf())

    /**
     * Syncs, then goes round again while rows written meanwhile are waiting, up to [maxRounds]. Rows
     * that failed in this run are left for the next one rather than retried in each round.
     */
    suspend fun syncUntilSettled(session: Session, maxRounds: Int): SyncReport {
        val failed = mutableSetOf<FailedRow>()
        var report = SyncReport.NONE
        var round = 0
        do {
            report += syncRound(session, failed)
        } while (++round < maxRounds && hasPending(failed))
        return report
    }

    /** Whether any table still has rows to push, e.g. ones written while a sync was running. */
    suspend fun hasPending(): Boolean = hasPending(emptySet())

    private suspend fun hasPending(failed: Set<FailedRow>): Boolean = tables.any { table -> table.pending(failed).isNotEmpty() }

    private suspend fun <E> SyncTable<E>.pending(failed: Set<FailedRow>) = dirty().filterNot { FailedRow(collection, id(it), updatedAt(it)) in failed }

    private suspend fun syncRound(session: Session, failed: MutableSet<FailedRow>): SyncReport = tables.fold(SyncReport.NONE) { report, table -> report + syncTable(session, table, failed) }

    /** A row that failed to push at this version; an edit after that makes it worth trying again. */
    private data class FailedRow(val collection: String, val id: String, val updatedAt: Long)

    private sealed interface Push {
        data object Pushed : Push
        data object Stale : Push
        data object Rejected : Push
    }

    private suspend fun <E> syncTable(session: Session, table: SyncTable<E>, failed: MutableSet<FailedRow>): SyncReport {
        val since = cursors.cursor(table.collection)
        // Before this table's first pull, most dirty rows have never been on the server.
        val likelyNew = since == null
        val outcomes = table.pending(failed).chunked(PARALLEL_PUSHES).flatMap { chunk ->
            coroutineScope {
                chunk.map { row -> async { row to push(session, table, row, likelyNew) } }.awaitAll()
            }
        }
        table.markClean(outcomes.filter { it.second == Push.Pushed }.map { it.first })
        outcomes.filter { it.second == Push.Rejected }.forEach { (row, _) -> failed += FailedRow(table.collection, table.id(row), table.updatedAt(row)) }

        val records = client.changesSince(session.serverUrl, session.token, table.collection, since)
        val remote = records.mapNotNull(table::fromJson)
        var pulled = table.saveIfNewer(remote)
        records.lastOrNull()?.get("updated")?.jsonPrimitive?.contentOrNull?.let { cursors.setCursor(table.collection, it) }

        // A refused-as-older row is usually replaced by the pull. Not when the server's copy hasn't
        // changed since the cursor (this device pulled it, then edited it with a clock running
        // behind): fetch that copy, or the row would stay dirty and be refused on every sync.
        val pulledIds = remote.mapTo(HashSet(), table::id)
        outcomes.filter { it.second == Push.Stale && table.id(it.first) !in pulledIds }.forEach { (row, _) ->
            val copy = client.record(session.serverUrl, session.token, table.collection, table.id(row))?.let(table::fromJson)
            if (copy == null) {
                failed += FailedRow(table.collection, table.id(row), table.updatedAt(row))
            } else {
                pulled += table.saveIfNewer(listOf(copy))
            }
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
        // The server's copy is newer; it replaces ours below.
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
    }
}

/** Cursors of [userId]'s session; writes are dropped once someone else (or nobody) is signed in. */
internal class SessionCursorStore(private val store: SessionStore, private val userId: String) : CursorStore {
    override suspend fun cursor(collection: String) = store.cursor(collection)
    override suspend fun setCursor(collection: String, value: String) {
        store.setCursor(userId, collection, value)
    }
}
