package dev.southstar21.kaloria.core.sync

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.dao.GoalsDao
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.network.PocketBaseAuthException
import dev.southstar21.kaloria.core.network.PocketBaseClient
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import kotlinx.serialization.SerializationException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessions: SessionStore,
    private val client: PocketBaseClient,
    foods: FoodDao,
    logEntries: LogEntryDao,
    weight: WeightDao,
    goals: GoalsDao,
    private val widget: WidgetUpdater,
) : CoroutineWorker(context, params) {

    // Foods first, so entries never arrive before the food they were logged from.
    private val engine = SyncEngine(
        client,
        SessionCursorStore(sessions),
        listOf(FoodsTable(foods), LogEntriesTable(logEntries), WeightTable(weight), GoalsTable(goals)),
    )

    override suspend fun doWork(): Result {
        val session = sessions.current() ?: return Result.success()
        return try {
            // Refreshing first both validates the session and keeps the 30-day token alive.
            val refreshed = client.refresh(session.serverUrl, session.token)
            sessions.updateToken(refreshed.token)
            val live = session.copy(token = refreshed.token)
            // Writes made while this run was going would otherwise wait for the next one, since
            // requests are dropped while a run is in progress. A row the server keeps refusing
            // stays dirty, hence the cap.
            var round = 0
            var pulled = 0
            do {
                pulled += engine.sync(live).pulled
            } while (++round < MAX_ROUNDS && engine.hasPending())
            if (pulled > 0) widget.requestUpdate()
            sessions.recordSuccess(System.currentTimeMillis())
            Result.success()
        } catch (e: PocketBaseAuthException) {
            sessions.recordError(e.message ?: "Signed out")
            sessions.clear()
            Result.failure()
        } catch (e: IOException) {
            sessions.recordError("Server unreachable")
            retryOrGiveUp()
        } catch (e: ResponseException) {
            sessions.recordError("Server error ${e.response.status.value}")
            retryOrGiveUp()
        } catch (e: SerializationException) {
            sessions.recordError("Unexpected server response")
            Result.failure()
        } catch (e: SQLiteException) {
            sessions.recordError("Local database error")
            Result.failure()
        }
    }

    /** Backs off a few times, then stops; the next write or the hourly run tries again. */
    private fun retryOrGiveUp(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    private companion object {
        const val MAX_ROUNDS = 3
        const val MAX_ATTEMPTS = 5
    }
}
