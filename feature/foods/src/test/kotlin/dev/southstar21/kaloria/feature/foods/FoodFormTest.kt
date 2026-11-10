package dev.southstar21.kaloria.feature.foods.editor

import dev.southstar21.kaloria.core.model.Micronutrient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodFormTest {
    private val valid = FoodForm(
        name = "Trappista sajt",
        energy = "357",
        protein = "26",
        carbohydrates = "0",
        sugars = "0",
        fat = "28",
        saturatedFat = "18",
        salt = "1,7",
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

    @Test
    fun `servings need an amount above zero`() {
        assertTrue(valid.copy(servings = listOf(ServingInput("slice", "0"))).errors.servings)
        assertTrue(valid.copy(servings = listOf(ServingInput("slice", ""))).errors.servings)
        assertTrue(valid.copy(servings = listOf(ServingInput("slice", "20000"))).errors.servings)
        assertFalse(valid.copy(servings = listOf(ServingInput("slice", "28"), ServingInput())).errors.servings)
    }

    @Test
    fun `a micronutrient over 100 g per 100 is impossible`() {
        assertTrue(valid.copy(micros = mapOf(Micronutrient.Sodium to "150000")).errors.implausible)
        assertTrue(valid.copy(micros = mapOf(Micronutrient.TransFat to "101")).errors.implausible)
        assertFalse(valid.copy(micros = mapOf(Micronutrient.Sodium to "680", Micronutrient.VitaminD to "2.5")).errors.implausible)
    }

    @Test
    fun `text longer than sync allows blocks the save`() {
        assertTrue(valid.copy(name = "a".repeat(201)).errors.tooLong)
        assertTrue(valid.copy(brand = "b".repeat(201)).errors.tooLong)
        assertTrue(valid.copy(barcode = "1".repeat(33)).errors.tooLong)
        assertTrue(valid.copy(name = "a".repeat(201)).errors.any)
        assertFalse(valid.copy(name = "a".repeat(200), brand = "b".repeat(200), barcode = "1".repeat(32)).errors.tooLong)
    }

    @Test
    fun `typing stops at the limit and a paste is cut there`() {
        assertEquals("a".repeat(199) + "b", ("a".repeat(199) + "bc").capped("a".repeat(199), 200))
        assertEquals("abc", "abc".capped("ab", 200))
        // An emoji cut in half is dropped whole.
        assertEquals("a".repeat(199), ("a".repeat(199) + "\uD83C\uDF4E").capped("a".repeat(199), 200))
    }

    @Test
    fun `prefilled text over the limit can only get shorter`() {
        val long = "a".repeat(250)
        assertEquals(long.dropLast(1), long.dropLast(1).capped(long, 200))
        assertEquals(long, (long + "x").capped(long, 200))
    }
}
