package dev.southstar21.kaloria.core.sync

import android.content.Context
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
import dev.southstar21.kaloria.core.network.PocketBaseClient

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    sessions: SessionStore,
    client: PocketBaseClient,
    foods: FoodDao,
    logEntries: LogEntryDao,
    weight: WeightDao,
    goals: GoalsDao,
    widget: WidgetUpdater,
    lock: SyncLock,
    scheduler: SyncScheduler,
) : CoroutineWorker(context, params) {

    private val runner = SyncRunner(
        sessions = SessionStoreSyncSessions(sessions),
        client = client,
        // Foods first, so entries never arrive before the food they were logged from.
        tables = listOf(FoodsTable(foods), LogEntriesTable(logEntries), WeightTable(weight), GoalsTable(goals)),
        lock = lock,
        now = System::currentTimeMillis,
        onPulled = widget::requestUpdate,
        // Signed out by the server: the hourly run has nothing left to do.
        onSignedOut = scheduler::cancelPeriodic,
    )

    override suspend fun doWork(): Result = when (runner.run()) {
        SyncRunner.Outcome.Success -> Result.success()
        SyncRunner.Outcome.Retry -> retryOrGiveUp()
        SyncRunner.Outcome.Failure -> Result.failure()
    }

    /** Backs off a few times, then stops; the next write or the hourly run tries again. */
    private fun retryOrGiveUp(): Result = if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
