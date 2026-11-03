package dev.southstar21.kaloria.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BodyDaosTest {
    private val db = inMemoryDatabase()
    private val weights = db.weightDao()
    private val goals = db.goalsDao()

    @After fun close() = db.close()

    @Test
    fun `legacyImports finds only hc-prefixed ids`() = runTest {
        weights.upsertAll(listOf(weight("hc:abc"), weight("abc"), weight("xhc:1"), weight("hc:def", deleted = true)))

        assertEquals(setOf("hc:abc", "hc:def"), weights.legacyImports().map { it.id }.toSet())
    }

    @Test
    fun `replace drops the old rows and writes the new ones`() = runTest {
        weights.upsertAll(listOf(weight("hc:1", kilograms = 70.0), weight("hc:2", kilograms = 71.0), weight("keep")))

        weights.replace(old = listOf("hc:1", "hc:2"), new = listOf(weight("new1", kilograms = 70.0, healthConnectId = "1")))

        assertEquals(setOf("keep", "new1"), weights.allSince(0).map { it.id }.toSet())
        assertEquals("1", weights.byIds(listOf("new1")).single().healthConnectId)
    }

    @Test
    fun `applyImport upserts and soft deletes in one go`() = runTest {
        weights.upsertAll(listOf(weight("vanished", dirty = false), weight("stays", dirty = false)))

        weights.applyImport(upserts = listOf(weight("fresh", healthConnectId = "hc")), deletedIds = listOf("vanished"), now = 42)

        val all = weights.allSince(0).associateBy { it.id }
        assertEquals(setOf("vanished", "stays", "fresh"), all.keys)
        assertTrue(all.getValue("vanished").deleted)
        assertTrue(all.getValue("vanished").dirty)
        assertEquals(42L, all.getValue("vanished").updatedAt)
        assertFalse(all.getValue("stays").deleted)
        assertFalse(all.getValue("stays").dirty)
    }

    @Test
    fun `applyImport with nothing to do changes nothing`() = runTest {
        weights.upsertAll(listOf(weight("a", dirty = false)))

        weights.applyImport(emptyList(), emptyList(), now = 1)

        assertEquals(emptyList<String>(), weights.dirty().map { it.id })
    }

    @Test
    fun `latest ignores deleted weigh-ins`() = runTest {
        weights.upsertAll(listOf(weight("old", loggedAt = 1), weight("newer", loggedAt = 2), weight("newest", loggedAt = 3, deleted = true)))

        assertEquals("newer", weights.latest().first()?.id)
    }

    @Test
    fun `saveCurrent creates the row once and then saves over it`() = runTest {
        assertNull(goals.current())

        goals.saveCurrent("first") { id -> goals(id, energyKcal = 1800, updatedAt = 1) }
        goals.saveCurrent("second") { id -> goals(id, energyKcal = 2100, updatedAt = 2) }

        assertEquals(listOf("first"), goals.byIds(listOf("first", "second")).map { it.id })
        assertEquals(2100, goals.current()?.energyKcal)
        assertEquals(2100, goals.observe().first()?.energyKcal)
    }
}
