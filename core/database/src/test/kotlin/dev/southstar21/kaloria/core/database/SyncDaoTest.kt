package dev.southstar21.kaloria.core.database

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncDaoTest {
    private val db = inMemoryDatabase()
    private val foods = db.foodDao()
    private val log = db.logEntryDao()

    @After fun close() = db.close()

    @Test
    fun `dirty returns only rows changed since the last push`() = runTest {
        foods.upsertAll(listOf(food("a", "A", dirty = true), food("b", "B", dirty = false), food("c", "C", dirty = true, deleted = true)))

        assertEquals(setOf("a", "c"), foods.dirty().map { it.id }.toSet())
    }

    @Test
    fun `markCleanAll only clears rows not edited again since they were pushed`() = runTest {
        foods.upsertAll(listOf(food("a", "A", updatedAt = 10), food("b", "B", updatedAt = 20)))
        // b is edited again while its push (of updatedAt 20) is in flight.
        foods.upsert(food("b", "B2", updatedAt = 25))

        foods.markCleanAll(mapOf("a" to 10L, "b" to 20L))

        assertEquals(listOf("b"), foods.dirty().map { it.id })
        assertFalse(foods.get("a")!!.dirty)
        assertTrue(foods.get("b")!!.dirty)
    }

    @Test
    fun `softDelete keeps a dirty tombstone with the new time`() = runTest {
        log.upsertAll(listOf(entry("e", loggedAt = 1, updatedAt = 5, dirty = false)))

        log.softDelete("e", now = 99)

        val row = log.get("e")!!
        assertTrue(row.deleted)
        assertTrue(row.dirty)
        assertEquals(99L, row.updatedAt)
        assertEquals(listOf("e"), log.dirty().map { it.id })
    }

    @Test
    fun `byIds returns deleted rows too`() = runTest {
        foods.upsertAll(listOf(food("a", "A"), food("b", "B", deleted = true), food("c", "C")))

        assertEquals(setOf("a", "b"), foods.byIds(listOf("a", "b", "missing")).map { it.id }.toSet())
    }
}
