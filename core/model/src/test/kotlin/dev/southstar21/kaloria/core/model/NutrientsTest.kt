package dev.southstar21.kaloria.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutrientsTest {
    private val oats = Nutrients(
        energyKcal = 372.0,
        protein = 13.5,
        carbohydrates = 58.7,
        fat = 7.0,
        fiber = 10.0,
        salt = 0.02,
        micros = mapOf(Micronutrient.Iron to 4.0),
    )

    @Test
    fun `scaledTo is linear in the amount`() {
        val portion = oats.scaledTo(50.0)
        assertEquals(186.0, portion.energyKcal!!, 1e-9)
        assertEquals(6.75, portion.protein!!, 1e-9)
        assertEquals(2.0, portion.micros[Micronutrient.Iron]!!, 1e-9)
    }

    @Test
    fun `unknown values stay unknown instead of becoming zero`() {
        val sum = Nutrients(energyKcal = 100.0) + Nutrients(energyKcal = 50.0, protein = 3.0)
        assertEquals(150.0, sum.energyKcal!!, 1e-9)
        assertEquals(3.0, sum.protein!!, 1e-9)
        assertNull(sum.fat)
    }

    @Test
    fun `sodium is derived from salt when not stated`() {
        assertEquals(8.0, oats.sodiumMg!!, 1e-9)
    }

    @Test
    fun `atwater energy matches a consistent label`() {
        // 4·13.5 + 4·58.7 + 9·7 + 2·10 = 371.8
        assertEquals(371.8, oats.atwaterKcal!!, 1e-9)
    }
}
