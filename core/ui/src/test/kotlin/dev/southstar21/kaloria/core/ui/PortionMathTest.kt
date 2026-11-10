package dev.southstar21.kaloria.core.ui

import dev.southstar21.kaloria.core.model.PortionUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionMathTest {
    @Test
    fun `an amount still shown as it was set keeps its exact value`() {
        // 330 ml shows as 11.16 fl oz; logging that must still log 330 ml.
        val shown = PortionUnit.FluidOunce.format(330.0)
        assertEquals("11.16", shown)
        assertEquals(330.0, portionAmount(shown, 330.0, PortionUnit.FluidOunce)!!, 0.0)
        // Typed over, it's what was typed.
        assertEquals(12 * 29.5735295625, portionAmount("12", 330.0, PortionUnit.FluidOunce)!!, 1e-9)
        assertNull(portionAmount("", 330.0, PortionUnit.FluidOunce))
    }

    @Test
    fun `a portion must be more than nothing and no more than the maximum`() {
        assertFalse(isValidPortion(null))
        assertFalse(isValidPortion(0.0))
        assertTrue(isValidPortion(0.5))
        assertTrue(isValidPortion(MAX_PORTION))
        assertFalse(isValidPortion(MAX_PORTION + 1))
        assertFalse(isValidPortion(99_999_999.0))
    }

    @Test
    fun `switching unit converts the amount`() {
        val (exact, text) = switchUnit("1", 1000.0, PortionUnit.Kilogram, PortionUnit.Gram)
        assertEquals(1000.0, exact, 0.0)
        assertEquals("1000", text)
    }

    @Test
    fun `switching unit with nothing typed leaves the field empty`() {
        val (exact, text) = switchUnit("", 250.0, PortionUnit.Gram, PortionUnit.Ounce)
        assertEquals("", text)
        assertEquals(250.0, exact, 0.0)
    }

    @Test
    fun `renaming a serving shown in ounces keeps its exact grams`() {
        val edit = ServingEdit(index = 0, name = "slice", amount = PortionUnit.Ounce.format(28.0), base = 28.0)
        assertEquals("0.99", edit.amount)

        assertEquals(28.0, servingAmount(edit, edit.amount, PortionUnit.Ounce)!!, 0.0)
        // Once the amount itself is edited, it's what was typed.
        assertEquals(28.349523125, servingAmount(edit, "1", PortionUnit.Ounce)!!, 1e-9)
    }

    @Test
    fun `a serving needs a valid amount`() {
        val edit = ServingEdit(index = null, name = "", amount = "", base = null)
        assertNull(servingAmount(edit, "", PortionUnit.Gram))
        assertNull(servingAmount(edit, "0", PortionUnit.Gram))
        assertNull(servingAmount(edit, "20000", PortionUnit.Gram))
        assertEquals(30.0, servingAmount(edit, "30", PortionUnit.Gram)!!, 0.0)
    }
}
