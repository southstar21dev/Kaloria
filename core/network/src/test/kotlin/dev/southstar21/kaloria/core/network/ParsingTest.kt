package dev.southstar21.kaloria.core.network

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses responses recorded from the real APIs (src/test/resources/fixtures). */
class ParsingTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    private fun client(body: String) = HttpClient(MockEngine { request ->
        assertTrue(request.headers[HttpHeaders.UserAgent]!!.startsWith("Kaloria/"))
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }).configured()

    @Test
    fun `off product is parsed per 100 g with micros converted from grams`() = runTest {
        val food = OpenFoodFactsDataSource(client(fixture("off_product_trappista.json"))).product("5998200467004")!!
        assertEquals("Trappista sajt", food.name)
        assertEquals("Coop", food.brand)
        assertEquals(AmountUnit.Gram, food.unit)
        assertEquals(320.0, food.nutrients.energyKcal!!, 0.0)
        assertEquals(16.0, food.nutrients.saturatedFat!!, 0.0)
        assertEquals(1.8, food.nutrients.salt!!, 0.0)
        assertEquals(720.0, food.nutrients.micros[Micronutrient.Sodium]!!, 1e-6)
        assertEquals(14.0, food.servings.single().amount, 0.0)
    }

    @Test
    fun `unknown barcode is null`() = runTest {
        assertNull(OpenFoodFactsDataSource(client(fixture("off_product_missing.json"))).product("0000000000000"))
    }

    @Test
    fun `drinks are per 100 ml`() = runTest {
        val hits = OpenFoodFactsDataSource(client(fixture("off_search_cola.json"))).search("coca cola")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.unit == AmountUnit.Millilitre })
        assertEquals("coca cola", hits.first().brand)
    }

    @Test
    fun `usda foundation foods use atwater energy and SR legacy uses kcal`() = runTest {
        val foods = UsdaDataSource(client(fixture("usda_search_apple.json")), "test").search("apple raw")
        val fuji = foods.first { it.name.startsWith("Apples, fuji") }
        assertEquals(58.2, fuji.nutrients.energyKcal!!, 0.0) // Atwater specific (2048)
        assertEquals(15.7, fuji.nutrients.carbohydrates!!, 0.0)
        assertEquals("USDA Foundation", fuji.detail)
        val rose = foods.first { it.name.startsWith("Rose-apples") }
        assertTrue(rose.nutrients.energyKcal!! in 20.0..30.0)
    }

    @Test
    fun `usda search mixing foundation, SR legacy and FNDDS parses`() = runTest {
        val foods = UsdaDataSource(client(fixture("usda_search_banana.json")), "test").search("banana raw")
        assertTrue(foods.size > 10)
        assertTrue(foods.all { it.nutrients.energyKcal != null })
    }
}
