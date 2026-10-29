package dev.southstar21.kaloria.feature.foods.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodFormTest {
    private val valid = FoodForm(
        name = "Trappista sajt", energy = "357", protein = "26", carbohydrates = "0", sugars = "0",
        fat = "28", saturatedFat = "18", salt = "1,7",
    )

    @Test
    fun `a consistent label has no errors or energy warning`() {
        assertFalse(valid.errors.any)
        assertNull(valid.energyMismatchKcal)
        assertEquals(1.7, valid.nutrients().salt!!, 1e-9)
    }

    @Test
    fun `kilojoules are converted to kcal`() {
        val form = valid.copy(energy = "1494", energyUnit = EnergyUnit.Kj)
        assertEquals(357.07, form.energyKcal!!, 0.01)
    }

    @Test
    fun `parts larger than the whole are errors`() {
        assertTrue(valid.copy(saturatedFat = "30").errors.saturatedOverFat)
        assertTrue(valid.copy(carbohydrates = "5", sugars = "6").errors.sugarsOverCarbs)
        assertTrue(valid.copy(protein = "60", fat = "50").errors.macrosOver100)
    }

    @Test
    fun `energy far from the macros is flagged`() {
        // 4·26 + 9·28 = 356; stating 557 kcal is a misread digit.
        assertNotNull(valid.copy(energy = "557").energyMismatchKcal)
    }

    @Test
    fun `impossible values per 100 g are rejected`() {
        assertTrue(valid.copy(energy = "3572602818").errors.implausible)
        assertTrue(valid.copy(energy = "4000", energyUnit = EnergyUnit.Kj).errors.implausible)
        assertTrue(valid.copy(salt = "120").errors.implausible)
        assertFalse(valid.copy(energy = "899", fat = "99.9", protein = "0", saturatedFat = "60").errors.implausible)
    }

    @Test
    fun `name and energy are required`() {
        val errors = FoodForm().errors
        assertTrue(errors.name)
        assertTrue(errors.energy)
    }
}
