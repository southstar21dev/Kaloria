package dev.southstar21.kaloria.core.data.health

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.data.di.IoDispatcher
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.database.model.VANISHED_FROM_HEALTH_CONNECT
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.database.model.nextUpdatedAt
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Two-way weight sync with Health Connect, and through it Samsung Health.
 *
 * Out: Kaloria's own weigh-ins are written with their Kaloria id and updated time as the client
 * record id and version, so writing them again is a no-op, and deleting one here deletes it there.
 * Only versions not exported yet are sent, plus a full pass once a day in case Health Connect lost
 * them (e.g. its data was cleared).
 *
 * In: other apps' weigh-ins are stored with [WeightEntryEntity.healthConnectId] set and an id derived
 * from the reading itself (time and weight), so reading them again doesn't duplicate them, and two
 * devices that see the same Samsung Health weigh-in agree on its id when they sync. One that
 * disappears from Health Connect is deleted here, but only on a device that imported it (another
 * device's Health Connect may simply never have had it), and only if it's old enough to be readable.
 * That deletion is marked, so the weigh-in comes back if the reading reappears, e.g. after Samsung
 * Health rewrites cleared data. Health Connect only lets an app delete its own records, so deleting
 * an imported weigh-in in Kaloria only hides it here; the kept tombstone stops it coming back.
 */
@Singleton
class HealthWeightSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: WeightDao,
    private val health: HealthWeights,
    private val clock: Clock,
    private val sync: SyncRequester,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    /** Ids this device imported (the only ones it may delete when they vanish) and sync state. Only read on [io]. */
    private val prefs by lazy { context.getSharedPreferences("health_weight_sync", Context.MODE_PRIVATE) }

    private val _accessState = MutableStateFlow<HealthAccess?>(null)

    /** The last known access, kept app-wide so screens show the right state straight away. */
    val accessState: StateFlow<HealthAccess?> = _accessState.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** Whether the last round failed to write to or read from Health Connect; the next one tries again. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    suspend fun access(): HealthAccess = health.access().also { _accessState.value = it }

    /** The Health Connect permissions to ask for: what weight sync needs, and reading older history. */
    val permissions: Set<String> get() = health.permissions + health.optionalPermissions

    /** Feed to rememberLauncherForActivityResult to ask for [permissions]. */
    fun permissionContract() = health.permissionContract()

    /**
     * [sync], unless one succeeded in the last few minutes. For coming back to the app, which happens
     * far more often than weigh-ins change.
     */
    suspend fun syncIfStale(): Boolean = withContext(io) {
        mutex.withLock {
            // Checked under the lock, so callers arriving together run one round between them.
            val last = prefs.getLong(KEY_LAST_SYNC, 0L)
            clock.millis() - last in 0 until THROTTLE.toMillis() || syncLocked()
        }
    }

    /**
     * Runs one round both ways. Returns false when Health Connect isn't available or allowed, or the
     * round failed ([failed]). Call it while Kaloria is in the foreground: Health Connect refuses
     * background reads of other apps' data.
     */
    suspend fun sync(): Boolean = withContext(io) { mutex.withLock { syncLocked() } }

    private suspend fun syncLocked(): Boolean {
        if (access() != HealthAccess.Granted) return false
        _syncing.value = true
        try {
            return syncRound().also { _failed.value = !it }
        } finally {
            _syncing.value = false
        }
    }

    /** Both directions, each failing on its own: a write Health Connect keeps refusing mustn't hold back imports. */
    private suspend fun syncRound(): Boolean = attempt("prepare") {
        migrateLegacyImports()
        val now = clock.instant()
        val local = dao.allSince(now.minus(WINDOW).toEpochMilli())
        val exported = attempt("export") { export(local, now) } != null
        val imported = attempt("import") { import(local, now) } != null
        if (exported && imported) prefs.edit { putLong(KEY_LAST_SYNC, clock.millis()) }
        exported && imported
    } == true

    private inline fun <T> attempt(step: String, block: () -> T): T? = suspendRunCatching(block)
        .onFailure { Log.w(TAG, "Health Connect weight sync: $step failed", it) }
        .getOrNull()

    /** Out: own weigh-ins in versions Health Connect hasn't been sent yet (all of them once a day). */
    private suspend fun export(local: List<WeightEntryEntity>, now: Instant) {
        val full = now.toEpochMilli() - prefs.getLong(KEY_LAST_FULL_EXPORT, 0L) >= FULL_EXPORT_EVERY.toMillis()
        val exported = prefs.getStringSet(KEY_EXPORTED, emptySet()).orEmpty()
        val own = local.filter { it.healthConnectId == null }
        // By version, not "changed since the last export": rows pulled from another device keep its
        // time, which can be older than this device's last export.
        val pending = own.filter { it.exportKey() !in exported }
        health.write((if (full) own else pending).filterNot { it.deleted }.map { WeightToWrite(it.id, it.kilograms, Instant.ofEpochMilli(it.loggedAt), it.updatedAt) })
        // A deletion only has to reach Health Connect once; if it lost the record, it's gone anyway.
        health.delete(pending.filter { it.deleted }.map { it.id })
        prefs.edit {
            putStringSet(KEY_EXPORTED, own.mapTo(HashSet()) { it.exportKey() })
            if (full) putLong(KEY_LAST_FULL_EXPORT, now.toEpochMilli())
        }
    }

    /** In: everyone else's weigh-ins. */
    private suspend fun import(local: List<WeightEntryEntity>, now: Instant) {
        val since = now.minus(WINDOW)
        val until = now.plus(Duration.ofDays(1))
        // Without the history permission, Health Connect hides other apps' readings from more than 30
        // days before the first grant, and a regrant can move that date: older imports are unreadable,
        // not gone. The last 30 days are always readable.
        val readableFrom = if (health.canReadHistory()) since else maxOf(since, now.minus(HealthWeights.DEFAULT_HISTORY))
        val byId = local.associateBy { it.id }
        val importedHere = prefs.getStringSet(KEY_IMPORTED, emptySet()).orEmpty().toMutableSet()
        val seen = HashSet<String>()
        val upserts = ArrayList<WeightEntryEntity>()
        health.read(since, until).filterNot { it.fromKaloria }.forEach { remote ->
            val id = importedId(remote.time.toEpochMilli(), remote.kilograms)
            if (!seen.add(id)) return@forEach // the same reading twice in Health Connect
            importedHere += id
            val existing = byId[id]
            when {
                existing == null -> upserts += WeightEntryEntity(id, remote.kilograms, remote.time.toEpochMilli(), healthConnectId = remote.id, updatedAt = clock.millis())

                // Deleted only because it had gone missing from Health Connect: it's back.
                existing.deleted && existing.healthConnectId == VANISHED_FROM_HEALTH_CONNECT -> upserts += existing.copy(
                    healthConnectId = remote.id,
                    deleted = false,
                    dirty = true,
                    updatedAt = nextUpdatedAt(clock.millis(), existing.updatedAt),
                )

                // Any other tombstone was deleted here on purpose: don't bring it back.
                else -> Unit
            }
        }
        val readable = readableFrom.toEpochMilli() until until.toEpochMilli()
        val vanished = local
            .filter { it.healthConnectId != null && !it.deleted && it.id !in seen && it.id in importedHere && it.loggedAt in readable }
            .map { it.id }
        dao.applyImport(upserts, vanished, clock.millis())
        importedHere -= vanished.toSet()
        // Forget ids that have left the window; they can't be seen (or vanish) any more.
        importedHere.retainAll(local.mapTo(HashSet()) { it.id } + seen)
        prefs.edit { putStringSet(KEY_IMPORTED, importedHere) }
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
        prefs.edit { putStringSet(KEY_IMPORTED, imported.mapTo(HashSet()) { renamed[it]?.id ?: it }) }
    }

    private companion object {
        const val TAG = "HealthWeightSync"
        const val LEGACY_PREFIX = "hc:"
        const val KEY_IMPORTED = "imported_ids"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_EXPORTED = "exported_versions"
        const val KEY_LAST_FULL_EXPORT = "last_full_export"

        /** How far back both directions reach; older weigh-ins are left alone. */
        val WINDOW: Duration = Duration.ofDays(400)
        val THROTTLE: Duration = Duration.ofMinutes(15)
        val FULL_EXPORT_EVERY: Duration = Duration.ofDays(1)

        /** The same reading gets the same id on every device. */
        fun importedId(loggedAt: Long, kilograms: Double) = Ids.fromExternal("health-weight:$loggedAt:$kilograms")

        /** An own weigh-in in one version; ids are `[a-z0-9]`, so `@` can't be part of one. */
        fun WeightEntryEntity.exportKey() = "$id@$updatedAt"
    }
}
