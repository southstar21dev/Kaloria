package dev.southstar21.kaloria.core.database.model

/**
 * Every synced table carries these columns.
 * - [updatedAt]: epoch millis of the last local or remote edit; the larger one wins a conflict.
 * - [deleted]: soft delete, so the deletion itself can be synced.
 * - [dirty]: changed locally since the last successful push.
 */
interface SyncedEntity {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val dirty: Boolean
}

/**
 * The [SyncedEntity.updatedAt] for a local edit at [now] of a row last stamped [previous]: [now], or
 * just after [previous] when this device's clock reads earlier. A clock that's behind (or was set
 * back) would otherwise stamp the edit older than what it replaces, and lose the conflict to it.
 */
fun nextUpdatedAt(now: Long, previous: Long?): Long = if (previous == null) now else maxOf(now, previous + 1)
