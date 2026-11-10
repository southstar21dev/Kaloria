package dev.southstar21.kaloria.core.database

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What sync does to whole tables: applying pulled rows, and an account switch. */
@RunWith(RobolectricTestRunner::class)
class LocalDataDaoTest {
    private val db = inMemoryDatabase()
    private val foods = db.foodDao()
    private val local = db.localDataDao()

    @After fun close() = db.close()

    @Test
    fun `pulled rows replace only older local copies`() = runTest {
        foods.upsertAll(listOf(food("old", "Old", updatedAt = 10, dirty = false), food("new", "Mine", updatedAt = 30)))

        val written = foods.upsertIfNewer(
            listOf(food("old", "Theirs", updatedAt = 20, dirty = false), food("new", "Theirs", updatedAt = 20, dirty = false), food("fresh", "Fresh", dirty = false)),
            { it.id },
            { it.updatedAt },
        )

        assertEquals(2, written)
        assertEquals("Theirs", foods.get("old")?.name)
        assertEquals("Mine", foods.get("new")?.name)
        assertTrue(foods.get("new")!!.dirty)
        assertEquals("Fresh", foods.get("fresh")?.name)
    }

    @Test
    fun `an account switch can queue everything for upload again`() = runTest {
        foods.upsertAll(listOf(food("a", "A", dirty = false), food("b", "B", dirty = false, deleted = true)))
        db.weightDao().upsertAll(listOf(weight("w", dirty = false)))

        local.markAllDirty()

        assertEquals(setOf("a", "b"), foods.dirty().map { it.id }.toSet())
        assertEquals(listOf("w"), db.weightDao().dirty().map { it.id })
    }

    @Test
    fun `an account switch can empty every synced table`() = runTest {
        foods.upsertAll(listOf(food("a", "A")))
        db.logEntryDao().upsertAll(listOf(entry("e", loggedAt = 1)))
        db.weightDao().upsertAll(listOf(weight("w")))
        db.goalsDao().upsertAll(listOf(goals("g")))
        assertTrue(local.hasData())

        local.deleteAll()

        assertFalse(local.hasData())
        assertEquals(null, foods.get("a"))
    }

    @Test
    fun `data and synced rows are told apart`() = runTest {
        assertFalse(local.hasData())
        // Deleted rows aren't data anyone would miss.
        foods.upsertAll(listOf(food("gone", "Gone", deleted = true)))
        assertFalse(local.hasData())

        foods.upsertAll(listOf(food("a", "A", dirty = true)))
        assertTrue(local.hasData())
        assertFalse(local.hasSyncedRows())

        db.goalsDao().upsertAll(listOf(goals("g").copy(dirty = false)))
        assertTrue(local.hasSyncedRows())
    }
}
