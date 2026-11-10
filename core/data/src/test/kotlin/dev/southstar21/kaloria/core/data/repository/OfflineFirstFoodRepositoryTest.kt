package dev.southstar21.kaloria.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import dev.southstar21.kaloria.core.database.model.asEntity
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFirstFoodRepositoryTest {
    private val now = Instant.parse("2026-10-30T12:00:00Z")
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KaloriaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var syncRequests = 0

    private val repository = OfflineFirstFoodRepository(
        dao = db.foodDao(),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        sync = SyncRequester { syncRequests++ },
    )

    @After fun close() = db.close()

    private fun food(id: String = "f", name: String = "Oats") = Food(id = id, name = name, source = FoodSource.Manual, nutrients = Nutrients(energyKcal = 372.0), updatedAt = Instant.EPOCH)

    @Test
    fun `saving cuts text to the server's limits and lookups still find it`() = runTest {
        val longRef = "r".repeat(80)
        val longCode = "1".repeat(40)
        // An emoji straddling the limit is dropped whole rather than split in half.
        val name = "a".repeat(199) + "🍎" + "tail"
        val saved = repository.save(
            food().copy(name = name, brand = "  " + "b".repeat(300), barcode = longCode, source = FoodSource.OpenFoodFacts, sourceRef = longRef),
        )

        assertEquals("a".repeat(199), saved.name)
        assertEquals(200, saved.brand?.length)
        assertEquals(32, saved.barcode?.length)
        assertEquals(64, saved.sourceRef?.length)
        assertEquals(saved, repository.get("f"))
        assertEquals("f", repository.byBarcode(longCode)?.id)
        assertEquals("f", repository.bySource(FoodSource.OpenFoodFacts, longRef)?.id)
    }

    @Test
    fun `a save is stamped after a newer pulled version`() = runTest {
        val pulled = now.toEpochMilli() + 60_000
        db.foodDao().upsertAll(listOf(food().copy(updatedAt = Instant.ofEpochMilli(pulled)).asEntity(dirty = false)))

        val saved = repository.save(food(name = "Rolled oats"))

        assertEquals(pulled + 1, saved.updatedAt.toEpochMilli())
        assertEquals(pulled + 1, db.foodDao().get("f")!!.updatedAt)
        repository.delete("f")
        assertEquals(pulled + 2, db.foodDao().get("f")!!.updatedAt)
    }

    @Test
    fun `saving servings keeps the rest of the stored food`() = runTest {
        repository.save(food())
        // A sync pulls a rename after the portion sheet read the food.
        db.foodDao().upsertAll(listOf(db.foodDao().get("f")!!.copy(name = "Renamed", favorite = true, dirty = false)))

        repository.saveServings("f", listOf(Serving("Cup", 80.0)))

        val stored = repository.get("f")!!
        assertEquals("Renamed", stored.name)
        assertTrue(stored.favorite)
        assertEquals(listOf(Serving("Cup", 80.0)), stored.servings)
        assertTrue(db.foodDao().get("f")!!.dirty)
        assertEquals(2, syncRequests)

        repository.saveServings("missing", emptyList())
        assertNull(repository.get("missing"))
        assertEquals(2, syncRequests)
    }
}
