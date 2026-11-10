package dev.southstar21.kaloria.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext context: Context) : SyncRequester {
    private val workManager = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Soon after a local write. Writes in quick succession share one run: while a run is waiting
     * to start, more requests are dropped. A write made while one is already running is picked up
     * by that run, which goes round again while rows are still dirty (see [SyncWorker]).
     */
    override fun requestSync() = enqueue(delaySeconds = 3, ExistingWorkPolicy.KEEP)

    /** Now, replacing any run still waiting for its delay. */
    fun syncNow() = enqueue(delaySeconds = 0, ExistingWorkPolicy.REPLACE)

    /** Catches changes made on other devices while this one is idle. */
    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(online).build(),
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(ONE_TIME)
        cancelPeriodic()
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(PERIODIC)
    }

    val isSyncing: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    private fun enqueue(delaySeconds: Long, policy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(
            ONE_TIME,
            policy,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(online)
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    private companion object {
        const val ONE_TIME = "sync"
        const val PERIODIC = "sync_periodic"
    }
}
