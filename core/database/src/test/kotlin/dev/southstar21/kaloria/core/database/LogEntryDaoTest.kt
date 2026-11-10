package dev.southstar21.kaloria.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogEntryDaoTest {
    private val db = inMemoryDatabase()
    private val dao = db.logEntryDao()

    @After fun close() = db.close()

    @Test
    fun `logged quarter-hours are distinct and skip deleted entries`() = runTest {
        val quarter = 900_000L
        dao.upsertAll(
            listOf(
                entry("a", loggedAt = 10 * quarter),
                entry("b", loggedAt = 10 * quarter + 899_999), // same quarter-hour as a
                entry("c", loggedAt = 11 * quarter),
                entry("d", loggedAt = 500 * quarter + 1),
                entry("gone", loggedAt = 900 * quarter, deleted = true),
            ),
        )

        val quarters = dao.loggedQuarterHours().first()
        assertEquals(listOf(10L, 11L, 500L), quarters.sorted())
    }

    @Test
    fun `between is half-open, oldest first, and skips deleted entries`() = runTest {
        dao.upsertAll(
            listOf(
                entry("late", loggedAt = 300),
                entry("early", loggedAt = 100),
                entry("end", loggedAt = 400),
                entry("gone", loggedAt = 200, deleted = true),
            ),
        )

        assertEquals(listOf("early", "late"), dao.between(100, 400).first().map { it.id })
    }

    @Test
    fun `local saves and deletes never go back in time`() = runTest {
        dao.upsertAll(listOf(entry("pulled", loggedAt = 1, updatedAt = 10_000, dirty = false)))

        // Edited on a device whose clock is behind the one that wrote the pulled copy.
        val saved = dao.save(entry("pulled", loggedAt = 2, updatedAt = 9_000))
        assertEquals(10_001L, saved.updatedAt)
        assertEquals(10_001L, dao.get("pulled")?.updatedAt)
        assertEquals(2L, dao.get("pulled")?.loggedAt)

        // A new row and a clock that's ahead keep the time given.
        assertEquals(5L, dao.save(entry("new", loggedAt = 1, updatedAt = 5)).updatedAt)
        assertEquals(20_000L, dao.save(entry("pulled", loggedAt = 2, updatedAt = 20_000)).updatedAt)

        dao.softDelete("pulled", now = 15_000)
        assertEquals(20_001L, dao.get("pulled")?.updatedAt)
    }
}
