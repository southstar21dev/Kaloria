package dev.southstar21.kaloria.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.dao.GoalsDao
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.WaterDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.datastore.SessionStore
import dev.southstar21.kaloria.core.network.PocketBaseAuthException
import dev.southstar21.kaloria.core.network.PocketBaseClient
import io.ktor.client.plugins.ResponseException
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessions: SessionStore,
    private val client: PocketBaseClient,
    foods: FoodDao,
    logEntries: LogEntryDao,
    water: WaterDao,
    weight: WeightDao,
    goals: GoalsDao,
) : CoroutineWorker(context, params) {

    // Foods first, so entries never arrive before the food they were logged from.
    private val engine = SyncEngine(
        client,
        SessionCursorStore(sessions),
        listOf(FoodsTable(foods), LogEntriesTable(logEntries), WaterTable(water), WeightTable(weight), GoalsTable(goals)),
    )

    override suspend fun doWork(): Result {
        val session = sessions.current() ?: return Result.success()
        return try {
            // Refreshing first both validates the session and keeps the 30-day token alive.
            val refreshed = client.refresh(session.serverUrl, session.token)
            sessions.updateToken(refreshed.token)
            engine.sync(session.copy(token = refreshed.token))
            sessions.recordSuccess(System.currentTimeMillis())
            Result.success()
        } catch (e: PocketBaseAuthException) {
            sessions.recordError(e.message ?: "Signed out")
            sessions.clear()
            Result.failure()
        } catch (e: IOException) {
            sessions.recordError("Server unreachable")
            Result.retry()
        } catch (e: ResponseException) {
            sessions.recordError("Server error ${e.response.status.value}")
            Result.retry()
        }
    }
}
