package dev.southstar21.kaloria.feature.progress

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Nutrients
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressSummaryTest {
    private fun entry(day: LocalDate, kcalPer100: Double, protein: Double?, grams: Double) = LogEntry(
        id = "x$day$kcalPer100",
        foodId = null,
        foodName = "f",
        brand = null,
        amount = grams,
        unit = AmountUnit.Gram,
        nutrientsPer100 = Nutrients(energyKcal = kcalPer100, protein = protein),
        loggedAt = day.atTime(12, 0).toInstant(ZoneOffset.UTC),
    )

    @Test
    fun `averages ignore days with nothing logged`() {
        val mon = LocalDate.of(2026, 10, 26)
        val s = ProgressSummary.of(
            listOf(entry(mon, 200.0, 10.0, 500.0), entry(mon.plusDays(2), 100.0, null, 1000.0)),
            from = mon,
            toInclusive = mon.plusDays(6),
            zone = ZoneOffset.UTC,
        )
        assertEquals(7, s.days.size)
        assertEquals(2, s.loggedDays)
        assertEquals(1000.0, s.energy[0]!!, 0.0)
        assertNull(s.energy[1])
        assertEquals(1000.0, s.average.energyKcal!!, 0.0)
        // Protein is known for one day only; it's still averaged over both logged days.
        assertEquals(25.0, s.average.protein!!, 0.0)
    }
}
