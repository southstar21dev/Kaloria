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
