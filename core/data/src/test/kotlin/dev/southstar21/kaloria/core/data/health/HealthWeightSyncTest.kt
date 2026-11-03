package dev.southstar21.kaloria.core.data.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.health.HealthWeights
import dev.southstar21.kaloria.core.model.HealthAccess
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [HealthWeights] is a concrete class over the real Health Connect client, which isn't usable under
 * Robolectric, so only the "no access" path can be tested here: nothing is read, written or migrated.
 */
@RunWith(RobolectricTestRunner::class)
class HealthWeightSyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, KaloriaDatabase::class.java).allowMainThreadQueries().build()
    private var syncRequests = 0

    private val sync = HealthWeightSync(
        context = context,
        dao = db.weightDao(),
        health = HealthWeights(context),
        clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        sync = SyncRequester { syncRequests++ },
    )

    @After fun close() = db.close()

    @Test
    fun `without Health Connect access a sync does nothing`() = runTest {
        val legacy = WeightEntryEntity("hc:abc", 80.0, 1_000L, updatedAt = 1L, dirty = false)
        db.weightDao().upsertAll(listOf(legacy))

        assertFalse(sync.sync())
        assertFalse(sync.syncIfStale())

        val access = sync.accessState.value
        assertNotEquals(null, access)
        assertNotEquals(HealthAccess.Granted, access)
        assertFalse(sync.syncing.value)
        // The legacy import is only re-keyed during a real sync round.
        assertEquals(listOf(legacy), db.weightDao().legacyImports())
        assertEquals(0, syncRequests)
    }
}
