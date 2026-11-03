package dev.southstar21.kaloria.core.data.health

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.health.HealthWeights
import dev.southstar21.kaloria.core.health.WeightToWrite
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.Ids
import dev.southstar21.kaloria.core.model.suspendRunCatching
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Two-way weight sync with Health Connect, and through it Samsung Health.
 *
 * Out: Kaloria's own weigh-ins are written with their Kaloria id and updated time as the client
 * record id and version, so writing them again is a no-op, and deleting one here deletes it there.
 * Only the ones changed since the last export are sent, plus a full pass once a day in case Health
 * Connect lost them (e.g. its data was cleared).
 *
 * In: other apps' weigh-ins are stored with [WeightEntryEntity.healthConnectId] set and an id derived
 * from the reading itself (time and weight), so reading them again doesn't duplicate them, and two
 * devices that see the same Samsung Health weigh-in agree on its id when they sync. One that
 * disappears from Health Connect is deleted here, but only on the device that imported it: another
 * device's Health Connect may simply never have had it. Health Connect only lets an app delete its
 * own records, so deleting an imported weigh-in in Kaloria only hides it here; the kept tombstone
 * stops it coming back on the next sync.
 */
@Singleton
class HealthWeightSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: WeightDao,
    private val health: HealthWeights,
    private val clock: Clock,
    private val sync: SyncRequester,
) {
    private val mutex = Mutex()

    /** Ids this device imported (the only ones it may delete when they vanish) and sync timestamps. */
    private val prefs by lazy { context.getSharedPreferences("health_weight_sync", Context.MODE_PRIVATE) }

    private val _accessState = MutableStateFlow<HealthAccess?>(null)

    /** The last known access, kept app-wide so screens show the right state straight away. */
    val accessState: StateFlow<HealthAccess?> = _accessState.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    suspend fun access(): HealthAccess = health.access().also { _accessState.value = it }

    /** The Health Connect permissions weight sync needs. */
    val permissions: Set<String> get() = health.permissions

    /** Feed to rememberLauncherForActivityResult to ask for [permissions]. */
    fun permissionContract() = health.permissionContract()

    /**
     * [sync], unless one succeeded in the last few minutes. For coming back to the app, which happens
     * far more often than weigh-ins change.
     */
    suspend fun syncIfStale(): Boolean {
        val last = prefs.getLong(KEY_LAST_SYNC, 0L)
        if (clock.millis() - last in 0 until THROTTLE.toMillis()) return true
        return sync()
    }

    /**
     * Runs one round both ways. Returns false when Health Connect isn't available or allowed. Call
     * it while Kaloria is in the foreground: Health Connect refuses background reads of other apps' data.
     */
    suspend fun sync(): Boolean = mutex.withLock {
        if (access() != HealthAccess.Granted) return false
        _syncing.value = true
        try {
            suspendRunCatching { syncRound() }.isSuccess
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun syncRound() {
        migrateLegacyImports()
        val now = clock.instant()
        val since = now.minus(WINDOW)
        val local = dao.allSince(since.toEpochMilli())

        // Out: own weigh-ins changed since the last export (all of them once a day).
        val lastExport = prefs.getLong(KEY_LAST_EXPORT, 0L)
        val full = now.toEpochMilli() - prefs.getLong(KEY_LAST_FULL_EXPORT, 0L) >= FULL_EXPORT_EVERY.toMillis()
        val own = local.filter { it.healthConnectId == null && (full || it.updatedAt > lastExport) }
        health.write(own.filterNot { it.deleted }.map { WeightToWrite(it.id, it.kilograms, Instant.ofEpochMilli(it.loggedAt), it.updatedAt) })
        health.delete(own.filter { it.deleted }.map { it.id })
        prefs.edit().apply {
            putLong(KEY_LAST_EXPORT, now.toEpochMilli())
            if (full) putLong(KEY_LAST_FULL_EXPORT, now.toEpochMilli())
        }.apply()

        // In: everyone else's weigh-ins.
        val byId = local.associateBy { it.id }
        val importedHere = prefs.getStringSet(KEY_IMPORTED, emptySet()).orEmpty().toMutableSet()
        val seen = HashSet<String>()
        val upserts = ArrayList<WeightEntryEntity>()
        health.read(since, now.plus(Duration.ofDays(1))).filterNot { it.fromKaloria }.forEach { remote ->
            val id = importedId(remote.time.toEpochMilli(), remote.kilograms)
            if (!seen.add(id)) return@forEach // the same reading twice in Health Connect
            importedHere += id
            val existing = byId[id]
            // A tombstone means it was deleted here on purpose: don't bring it back.
            if (existing == null) {
                upserts += WeightEntryEntity(id, remote.kilograms, remote.time.toEpochMilli(), healthConnectId = remote.id, updatedAt = clock.millis())
            }
        }
        val vanished = local.filter { it.healthConnectId != null && !it.deleted && it.id !in seen && it.id in importedHere }.map { it.id }
        dao.applyImport(upserts, vanished, clock.millis())
        importedHere -= vanished.toSet()
        // Forget ids that have left the window; they can't be seen (or vanish) any more.
        importedHere.retainAll(local.mapTo(HashSet()) { it.id } + seen)
        prefs.edit().putStringSet(KEY_IMPORTED, importedHere).putLong(KEY_LAST_SYNC, clock.millis()).apply()
        if (upserts.isNotEmpty() || vanished.isNotEmpty()) sync.requestSync()
    }

    /**
     * Imports used to be stored as `hc:<Health Connect id>`, which the server rejects (ids are 15
     * chars of `[a-z0-9]`), so they never synced. Re-keys them in the current format, once.
     */
    private suspend fun migrateLegacyImports() {
        val legacy = dao.legacyImports()
        if (legacy.isEmpty()) return
        val renamed = legacy.associate { old ->
            old.id to old.copy(
                id = importedId(old.loggedAt, old.kilograms),
                healthConnectId = old.id.removePrefix(LEGACY_PREFIX),
                dirty = true,
            )
        }
        dao.replace(old = renamed.keys.toList(), new = renamed.values.distinctBy { it.id })
        val imported = prefs.getStringSet(KEY_IMPORTED, emptySet()).orEmpty()
        prefs.edit().putStringSet(KEY_IMPORTED, imported.mapTo(HashSet()) { renamed[it]?.id ?: it }).apply()
    }

    private companion object {
        const val LEGACY_PREFIX = "hc:"
        const val KEY_IMPORTED = "imported_ids"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_EXPORT = "last_export"
        const val KEY_LAST_FULL_EXPORT = "last_full_export"

        /** How far back both directions reach; older weigh-ins are left alone. */
        val WINDOW: Duration = Duration.ofDays(400)
        val THROTTLE: Duration = Duration.ofMinutes(15)
        val FULL_EXPORT_EVERY: Duration = Duration.ofDays(1)

        /** The same reading gets the same id on every device. */
        fun importedId(loggedAt: Long, kilograms: Double) = Ids.fromExternal("health-weight:$loggedAt:$kilograms")
    }
}
