package dev.southstar21.kaloria.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    private val pocketBaseId = Regex("[a-z0-9]{15}")

    @Test
    fun `new ids are in PocketBase format`() {
        repeat(100) { assertTrue(pocketBaseId.matches(Ids.newId())) }
    }

    @Test
    fun `external ids are valid and stable`() {
        val id = Ids.fromExternal("0b8e7f3a-6c3e-4c1e-9d0f-2f6f6c1a9b11")
        assertTrue(pocketBaseId.matches(id))
        assertEquals(id, Ids.fromExternal("0b8e7f3a-6c3e-4c1e-9d0f-2f6f6c1a9b11"))
        assertNotEquals(id, Ids.fromExternal("0b8e7f3a-6c3e-4c1e-9d0f-2f6f6c1a9b12"))
    }

    @Test
    fun `external ids stay valid for any key`() {
        listOf("", "a", "hc:🙂", "x".repeat(500)).forEach { assertTrue(it, pocketBaseId.matches(Ids.fromExternal(it))) }
    }
}
