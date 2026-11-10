package dev.southstar21.kaloria.core.database

import dev.southstar21.kaloria.core.database.dao.search
import dev.southstar21.kaloria.core.model.Serving
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodDaoTest {
    private val db = inMemoryDatabase()
    private val foods = db.foodDao()
    private val log = db.logEntryDao()

    @After fun close() = db.close()

    private suspend fun search(query: String) = foods.search(query).first().map { it.id }

    @Test
    fun `favorites come first, then the most recently logged, then by name`() = runTest {
        foods.upsertAll(
            listOf(
                food("never-b", "Banana"),
                food("never-a", "apple"),
                food("old", "Zucchini"),
                food("recent", "Yogurt"),
                food("fav", "Xigua", favorite = true),
            ),
        )
        log.upsertAll(
            listOf(
                entry("e1", loggedAt = 1_000, foodId = "old"),
                entry("e2", loggedAt = 5_000, foodId = "recent"),
                // A deleted entry doesn't count as logging the food.
                entry("e3", loggedAt = 9_000, foodId = "old", deleted = true),
            ),
        )

        // Names compare case-insensitively: "apple" before "Banana".
        assertEquals(listOf("fav", "recent", "old", "never-a", "never-b"), search(""))
    }

    @Test
    fun `matches name and brand substrings and the exact barcode`() = runTest {
        foods.upsertAll(
            listOf(
                food("name", "Greek yogurt"),
                food("brand", "Milk", brand = "Yogurtland"),
                food("barcode", "Cheese", barcode = "5991234567890"),
                food("other", "Bread", barcode = "1234"),
            ),
        )

        assertEquals(setOf("name", "brand"), search("ogurt").toSet())
        assertEquals(listOf("barcode"), search("5991234567890"))
        // A barcode only matches exactly, not as a substring.
        assertEquals(emptyList<String>(), search("123"))
    }

    @Test
    fun `percent and underscore are matched literally`() = runTest {
        foods.upsertAll(listOf(food("pct", "Juice 100%"), food("plain", "Juice 1000 ml"), food("under", "snake_case"), food("other", "snakeXcase")))

        assertEquals(listOf("pct"), search("100%"))
        assertEquals(listOf("under"), search("e_c"))
        assertEquals(emptyList<String>(), search("\\"))
    }

    @Test
    fun `non-ASCII letters match regardless of case`() = runTest {
        foods.upsertAll(listOf(food("upper", "Äpfel"), food("lower", "äpfel mus"), food("brand", "Saft", brand = "ÖKO"), food("other", "Xpfel")))

        assertEquals(setOf("upper", "lower"), search("äpfel").toSet())
        assertEquals(setOf("upper", "lower"), search("ÄPFEL").toSet())
        assertEquals(listOf("brand"), search("öko"))
    }

    @Test
    fun `save is stamped after the stored version and servings update alone`() = runTest {
        foods.upsertAll(listOf(food("f", "Rice", updatedAt = 1_000, dirty = false), food("gone", "Old", deleted = true)))

        assertEquals(1_001L, foods.save(food("f", "Rice", favorite = true, updatedAt = 900)).updatedAt)

        assertEquals(1, foods.updateServings("f", listOf(Serving("Cup", 180.0)), now = 500))
        val f = foods.get("f")!!
        assertEquals(listOf(Serving("Cup", 180.0)), f.servings)
        assertTrue(f.favorite) // the rest of the row is kept
        assertEquals(1_002L, f.updatedAt)
        assertTrue(f.dirty)
        assertEquals(0, foods.updateServings("gone", emptyList(), now = 500))
        assertEquals(0, foods.updateServings("missing", emptyList(), now = 500))
    }

    @Test
    fun `deleted foods are left out`() = runTest {
        foods.upsertAll(listOf(food("live", "Rice"), food("gone", "Rice cake", deleted = true)))

        assertEquals(listOf("live"), search("Rice"))
        assertEquals(listOf("live"), search(""))
    }

    @Test
    fun `limit caps the results`() = runTest {
        foods.upsertAll((1..5).map { food("f$it", "Food $it") })

        assertEquals(listOf("f1", "f2"), foods.search("", limit = 2).first().map { it.id })
    }
}
