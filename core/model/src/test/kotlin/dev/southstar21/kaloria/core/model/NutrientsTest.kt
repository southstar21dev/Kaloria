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
    fun `a total counts salt-only foods towards sodium even when another states sodium`() {
        val usda = Nutrients(micros = mapOf(Micronutrient.Sodium to 200.0))
        val euLabel = Nutrients(salt = 2.0) // 800 mg sodium
        val both = Nutrients(salt = 1.0, micros = mapOf(Micronutrient.Sodium to 300.0)) // sodium as stated, salt not added again

        assertEquals(1000.0, listOf(usda, euLabel).sum().sodiumMg!!, 1e-9)
        assertEquals(1000.0, listOf(euLabel, usda).sum().sodiumMg!!, 1e-9)
        assertEquals(1300.0, listOf(euLabel, usda, both).sum().sodiumMg!!, 1e-9)
        assertEquals(1300.0, (euLabel + (usda + both)).sodiumMg!!, 1e-9)
        // Salt itself is still only what the labels state.
        assertEquals(3.0, listOf(euLabel, usda, both).sum().salt!!, 1e-9)
    }

    @Test
    fun `sodium stays derived from salt while no food states it`() {
        val total = listOf(Nutrients(salt = 1.0), Nutrients(salt = 0.5)).sum()
        assertNull(total.micros[Micronutrient.Sodium])
        assertEquals(600.0, total.sodiumMg!!, 1e-9)
        assertNull(listOf(Nutrients(energyKcal = 1.0)).sum().sodiumMg)
    }

    @Test
    fun `atwater energy matches a consistent label`() {
        // 4·13.5 + 4·58.7 + 9·7 + 2·10 = 371.8
        assertEquals(371.8, oats.atwaterKcal!!, 1e-9)
    }
}
