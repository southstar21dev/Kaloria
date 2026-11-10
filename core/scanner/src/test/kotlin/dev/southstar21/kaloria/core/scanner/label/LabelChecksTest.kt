package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelChecksTest {
    private fun kinds(n: Nutrients) = LabelChecks.check(n).map { it.field to it.kind }.toSet()

    @Test
    fun `consistent values raise nothing`() {
        assertEquals(emptySet<Pair<String, WarningKind>>(), kinds(Nutrients(energyKcal = 320.0, protein = 24.0, carbohydrates = 2.0, sugars = 2.0, fat = 24.0, saturatedFat = 16.0)))
    }

    @Test
    fun `each check flags the editor's field`() {
        val found = kinds(Nutrients(energyKcal = 950.0, protein = 120.0, carbohydrates = 2.0, sugars = 5.0, fat = 1.0, saturatedFat = 3.0, salt = 101.0))
        listOf(
            "energy" to WarningKind.EnergyVsMacros,
            "energy" to WarningKind.EnergyOver920,
            "sugars" to WarningKind.SugarsOverCarbs,
            "saturatedFat" to WarningKind.SaturatedOverFat,
            "protein" to WarningKind.Over100Grams,
            "salt" to WarningKind.Over100Grams,
        ).forEach { assertTrue("$it in $found", it in found) }
    }

    @Test
    fun `missing core values are flagged, with an english fallback text`() {
        val warnings = LabelChecks.check(Nutrients(energyKcal = 320.0))
        assertEquals(setOf("protein", "carbohydrates", "fat"), warnings.filter { it.kind == WarningKind.NotRead }.map { it.field }.toSet())
        assertEquals("Not read from the label.", warnings.first().message)
        assertEquals("Energy doesn't match the macros (they imply 320 kcal).", FieldWarning("energy", WarningKind.EnergyVsMacros, listOf("320")).message)
    }
}
