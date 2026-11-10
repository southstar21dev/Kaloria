package dev.southstar21.kaloria.core.data.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import dev.southstar21.kaloria.core.database.model.VANISHED_FROM_HEALTH_CONNECT
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.health.HealthWeight
import dev.southstar21.kaloria.core.health.HealthWeights
import dev.southstar21.kaloria.core.health.WeightToWrite
import dev.southstar21.kaloria.core.model.HealthAccess
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthWeightSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, KaloriaDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.weightDao()
    private var syncRequests = 0
    private val clock = TestClock(Instant.parse("2026-10-30T12:00:00Z"))
    private val health = FakeHealthWeights()

    private val sync = HealthWeightSync(
        context = context,
        dao = dao,
        health = health,
        clock = clock,
        sync = SyncRequester { syncRequests++ },
        io = UnconfinedTestDispatcher(),
    )

    @After fun close() = db.close()

    private fun daysAgo(days: Long): Instant = clock.instant().minus(Duration.ofDays(days))

    /** A weigh-in another app wrote to Health Connect. */
    private fun reading(id: String, at: Instant, kilograms: Double = 80.0) = HealthWeight(id, kilograms, at, fromKaloria = false)

    /** The one row imported from Health Connect record [healthConnectId]. */
    private suspend fun imported(healthConnectId: String): WeightEntryEntity = dao.allSince(0).single { it.healthConnectId == healthConnectId }

    private suspend fun row(id: String): WeightEntryEntity = dao.byIds(listOf(id)).single()

    private fun later(minutes: Long = 1) {
        clock.now = clock.now.plus(Duration.ofMinutes(minutes))
    }

    @Test
    fun `without Health Connect access a sync does nothing`() = runTest {
        health.access = HealthAccess.NeedsPermission
        val legacy = WeightEntryEntity("hc:abc", 80.0, 1_000L, updatedAt = 1L, dirty = false)
        dao.upsertAll(listOf(legacy))

        assertFalse(sync.sync())
        assertFalse(sync.syncIfStale())

        assertEquals(HealthAccess.NeedsPermission, sync.accessState.value)
        assertFalse(sync.syncing.value)
        // The legacy import is only re-keyed during a real sync round.
        assertEquals(listOf(legacy), dao.legacyImports())
        assertEquals(0, health.reads)
        assertEquals(0, syncRequests)
    }

    @Test
    fun `permissions to ask for include the optional history`() {
        assertEquals(setOf("read", "write", "history"), sync.permissions)
    }

    @Test
    fun `imports other apps' weigh-ins once and re-keys legacy imports`() = runTest {
        dao.upsertAll(listOf(WeightEntryEntity("hc:old", 81.0, daysAgo(3).toEpochMilli(), updatedAt = 1L, dirty = false)))
        health.records += reading("samsung", daysAgo(1))
        health.records += HealthWeight("mine", 79.0, daysAgo(2), fromKaloria = true)

        assertTrue(sync.sync())
        assertTrue(sync.sync())

        val all = dao.allSince(0)
        assertEquals(setOf("samsung", "old"), all.mapNotNull { it.healthConnectId }.toSet())
        assertTrue(all.none { it.id.startsWith("hc:") })
        assertEquals(1, syncRequests)
    }

    @Test
    fun `a weigh-in that vanishes from Health Connect comes back when the reading does`() = runTest {
        val samsung = reading("samsung", daysAgo(5))
        health.records += samsung
        sync.sync()
        val id = imported("samsung").id

        // Samsung Health sharing turned off: the reading is gone.
        health.records.clear()
        sync.sync()
        assertTrue(row(id).deleted)
        assertEquals(VANISHED_FROM_HEALTH_CONNECT, row(id).healthConnectId)
        val tombstoneStamp = row(id).updatedAt

        // Turned back on: Samsung Health writes the same reading under a new record id.
        health.records += samsung.copy(id = "samsung-again")
        sync.sync()
        val back = row(id)
        assertFalse(back.deleted)
        assertTrue(back.dirty)
        assertEquals("samsung-again", back.healthConnectId)
        assertTrue(back.updatedAt > tombstoneStamp)
        assertEquals(3, syncRequests)
    }

    @Test
    fun `an import the user deleted stays deleted`() = runTest {
        health.records += reading("samsung", daysAgo(5))
        sync.sync()
        val id = imported("samsung").id

        dao.softDelete(id, clock.millis())
        sync.sync()

        assertTrue(row(id).deleted)
        assertEquals("samsung", row(id).healthConnectId)
    }

    @Test
    fun `without the history permission only the last 30 days can vanish`() = runTest {
        health.records += reading("recent", daysAgo(10))
        health.records += reading("older", daysAgo(100))
        sync.sync()
        val recent = imported("recent").id
        val older = imported("older").id

        // Access granted again later: Health Connect now hides what's older than 30 days.
        health.records.removeAll { it.id == "recent" || it.time < daysAgo(30) }
        sync.sync()
        assertTrue(row(recent).deleted)
        assertFalse(row(older).deleted)

        // With the history permission, the whole window is readable, so a missing one did vanish.
        health.history = true
        sync.sync()
        assertTrue(row(older).deleted)
    }

    @Test
    fun `own weigh-ins are exported by version, including ones pulled with an older time`() = runTest {
        dao.upsertAll(listOf(WeightEntryEntity("own1", 80.0, daysAgo(1).toEpochMilli(), updatedAt = clock.millis())))
        sync.sync()
        assertEquals(listOf("own1"), health.written.map { it.id })

        // Pulled from another device: its time is from before this device's last export.
        later()
        dao.upsertAll(listOf(WeightEntryEntity("pulled", 81.0, daysAgo(2).toEpochMilli(), updatedAt = clock.millis() - 3_600_000, dirty = false)))
        sync.sync()
        assertEquals(listOf("own1", "pulled"), health.written.map { it.id })

        // Nothing new: nothing sent again until the daily full pass.
        later()
        sync.sync()
        assertEquals(2, health.written.size)

        // A deletion goes out once, even across the full pass.
        dao.softDelete("pulled", clock.millis())
        later()
        sync.sync()
        assertEquals(listOf("pulled"), health.deleted)
        clock.now = clock.now.plus(Duration.ofDays(1))
        sync.sync()
        assertEquals(listOf("pulled"), health.deleted)
        assertEquals(listOf("own1", "pulled", "own1"), health.written.map { it.id })
    }

    @Test
    fun `a failing export doesn't block imports and is reported`() = runTest {
        dao.upsertAll(listOf(WeightEntryEntity("own", 80.0, daysAgo(1).toEpochMilli(), updatedAt = clock.millis())))
        health.records += reading("samsung", daysAgo(1))
        health.failWrites = true

        assertFalse(sync.sync())
        assertTrue(sync.failed.value)
        assertFalse(imported("samsung").deleted)
        // Not throttled after a failure: coming back to the app tries again.
        later()
        health.failWrites = false
        assertTrue(sync.syncIfStale())
        assertFalse(sync.failed.value)
        assertEquals(listOf("own"), health.written.map { it.id })
    }

    @Test
    fun `callers arriving together run one round`() = runTest {
        health.records += reading("samsung", daysAgo(1))

        val first = launch { sync.syncIfStale() }
        val second = launch { sync.syncIfStale() }
        first.join()
        second.join()

        assertEquals(1, health.reads)
        later(minutes = 16)
        sync.syncIfStale()
        assertEquals(2, health.reads)
    }

    @Test
    fun `a missing reading isn't deleted on a device that never imported it`() = runTest {
        // Synced from another device; this device's Health Connect never had the reading.
        dao.upsertAll(listOf(WeightEntryEntity("fromserver", 80.0, daysAgo(1).toEpochMilli(), healthConnectId = "elsewhere", updatedAt = 1, dirty = false)))

        sync.sync()

        assertFalse(row("fromserver").deleted)
        assertNotEquals(VANISHED_FROM_HEALTH_CONNECT, row("fromserver").healthConnectId)
        assertNull(health.written.firstOrNull())
    }
}

private class TestClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}

private class FakeHealthWeights : HealthWeights {
    var access = HealthAccess.Granted
    var history = false
    var failWrites = false
    var reads = 0

    /** What Health Connect holds. */
    val records = mutableListOf<HealthWeight>()
    val written = mutableListOf<WeightToWrite>()
    val deleted = mutableListOf<String>()

    override val permissions = setOf("read", "write")
    override val optionalPermissions = setOf("history")

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = throw UnsupportedOperationException()

    override suspend fun access() = access

    override suspend fun canReadHistory() = history

    override suspend fun read(from: Instant, to: Instant): List<HealthWeight> {
        reads++
        return records.filter { it.time >= from && it.time < to }
    }

    override suspend fun write(weights: List<WeightToWrite>) {
        check(!failWrites) { "Health Connect refused the write" }
        written += weights
    }

    override suspend fun delete(kaloriaIds: List<String>) {
        deleted += kaloriaIds
    }
}
