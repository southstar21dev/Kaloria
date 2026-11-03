package dev.southstar21.kaloria.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PortionUnitTest {
    @Test
    fun `a 330 ml can is a third of a litre and about 11 fluid ounces`() {
        assertEquals(0.33, PortionUnit.Litre.fromBase(330.0), 1e-9)
        assertEquals(11.16, PortionUnit.FluidOunce.fromBase(330.0), 0.01)
    }

    @Test
    fun `ounces and pounds convert to grams`() {
        assertEquals(28.35, PortionUnit.Ounce.toBase(1.0), 0.01)
        assertEquals(453.59, PortionUnit.Pound.toBase(1.0), 0.01)
        assertEquals(250.0, PortionUnit.Kilogram.toBase(0.25), 1e-9)
    }

    @Test
    fun `each base offers only its own units`() {
        assertEquals(listOf(PortionUnit.Gram, PortionUnit.Kilogram, PortionUnit.Ounce, PortionUnit.Pound), PortionUnit.forBase(AmountUnit.Gram))
        assertEquals(PortionUnit.Millilitre, PortionUnit.default(AmountUnit.Millilitre))
    }
}
