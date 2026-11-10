package dev.southstar21.kaloria.core.sync

import android.database.sqlite.SQLiteException
import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.network.PocketBaseAuthException
import dev.southstar21.kaloria.core.network.PocketBaseClient
import dev.southstar21.kaloria.core.network.UnexpectedResponseException
import io.ktor.client.plugins.ResponseException
import java.io.IOException

/** What a sync run reads from and records in the session store. */
internal interface SyncSessions {
    suspend fun current(): Session?
    suspend fun updateToken(userId: String, token: String)
    fun cursors(userId: String): CursorStore
    suspend fun recordSuccess(userId: String, at: Long, report: SyncReport)
    suspend fun recordError(userId: String, message: String)

    /** Signs out, keeping [message] to show why. */
    suspend fun expire(userId: String, message: String)
}

internal class SessionStoreSyncSessions(private val store: SessionStore) : SyncSessions {
    override suspend fun current() = store.current()
    override suspend fun updateToken(userId: String, token: String) {
        store.updateToken(userId, token)
    }
    override fun cursors(userId: String): CursorStore = SessionCursorStore(store, userId)
    override suspend fun recordSuccess(userId: String, at: Long, report: SyncReport) {
        store.recordSuccess(userId, at, rejected = report.rejected, stale = report.rejectedAsStale)
    }
    override suspend fun recordError(userId: String, message: String) {
        store.recordError(userId, message)
    }
    override suspend fun expire(userId: String, message: String) {
        store.expire(userId, message)
    }
}

/** One sync run and what its failures mean: the body of [SyncWorker], kept free of WorkManager. */
internal class SyncRunner(
    private val sessions: SyncSessions,
    private val client: PocketBaseClient,
    private val tables: List<SyncTable<*>>,
    private val lock: SyncLock,
    private val now: () -> Long,
    private val onPulled: () -> Unit,
    private val onSignedOut: () -> Unit,
) {
    enum class Outcome { Success, Retry, Failure }

    suspend fun run(): Outcome = lock.withLock {
        val session = sessions.current() ?: return@withLock Outcome.Success
        try {
            // Refreshing first both validates the session and keeps the 30-day token alive.
            val refreshed = client.refresh(session.serverUrl, session.token)
            sessions.updateToken(session.userId, refreshed.token)
            val live = session.copy(token = refreshed.token)
            // Writes made while this run was going would otherwise wait for the next one, since
            // requests are dropped while a run is in progress; hence the extra rounds.
            val report = SyncEngine(client, sessions.cursors(session.userId), tables).syncUntilSettled(live, MAX_ROUNDS)
            if (report.pulled > 0) onPulled()
            // Rows the server refused are counted, so the account card can say they didn't sync.
            sessions.recordSuccess(session.userId, now(), report)
            Outcome.Success
        } catch (e: PocketBaseAuthException) {
            sessions.expire(session.userId, e.message ?: "Signed out")
            onSignedOut()
            Outcome.Failure
        } catch (_: IOException) {
            sessions.recordError(session.userId, "Server unreachable")
            Outcome.Retry
        } catch (_: UnexpectedResponseException) {
            // e.g. a Wi-Fi sign-in page answering instead of the server; likely gone later.
            sessions.recordError(session.userId, "Unexpected server response")
            Outcome.Retry
        } catch (e: ResponseException) {
            sessions.recordError(session.userId, "Server error ${e.response.status.value}")
            Outcome.Retry
        } catch (_: SQLiteException) {
            sessions.recordError(session.userId, "Local database error")
            Outcome.Failure
        }
    }

    private companion object {
        /** A row the server keeps refusing is left out of later rounds, so this only caps writes arriving meanwhile. */
        const val MAX_ROUNDS = 3
    }
}
