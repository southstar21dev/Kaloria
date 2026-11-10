package dev.southstar21.kaloria.core.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One sync at a time in this process. The one-time and the periodic work are separate unique work,
 * so WorkManager may start both at once; two runs would push the same rows twice and race each
 * other's cursors. Sign-out and account switches take it too, so no run is half-way through when
 * the session or the local data changes under it.
 */
@Singleton
class SyncLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
