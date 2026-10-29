package dev.southstar21.kaloria.core.data.sync

/** Asks for a sync soon after a local write. Bound to the WorkManager scheduler by :core:sync. */
fun interface SyncRequester {
    fun requestSync()
}
