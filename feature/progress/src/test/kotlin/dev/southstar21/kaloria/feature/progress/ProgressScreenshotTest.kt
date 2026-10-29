package dev.southstar21.kaloria.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.WeightEntry
import dev.southstar21.kaloria.core.testing.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ProgressScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 29)
    private val zone = ZoneId.systemDefault()

    private fun state(range: ProgressRange): ProgressUiState {
        val from = today.minusDays(range.days - 1)
        val entries = (0 until range.days).filter { it % 4 != 2L }.map { d ->
            LogEntry(
                "e$d", null, "Food", null, 600.0 + (d * 37 % 300), AmountUnit.Gram,
                Nutrients(energyKcal = 300.0, protein = 18.0, carbohydrates = 30.0, fat = 11.0, fiber = 3.0, salt = 0.6,
                    micros = mapOf(Micronutrient.Calcium to 120.0, Micronutrient.VitaminC to 8.0)),
                from.plusDays(d).atTime(12, 0).atZone(zone).toInstant(),
            )
        }
        val weights = (0..8).map { i -> WeightEntry("w$i", 83.2 - i * 0.15 + (if (i % 3 == 0) 0.2 else 0.0), today.minusDays(80L - i * 10).atTime(7, 0).atZone(zone).toInstant()) }
        return ProgressUiState(range, ProgressSummary.of(entries, from, today, zone), weights, weights.last(),
            Goals(energyKcal = 2200, protein = 150, carbohydrates = 230, fat = 75, fiber = 30, steps = 9000))
    }

    private val steps = listOf(4200L, 9100L, null, 11800L, 7600L, 3000L, 9500L)

    @Test fun week_light() = compose.screenshot("progress_week", false, dock = true) { ProgressScreen(state(ProgressRange.Week), steps, {}, {}) }
    @Test fun week_dark() = compose.screenshot("progress_week", true, dock = true) { ProgressScreen(state(ProgressRange.Week), steps, {}, {}) }
    @Test fun month() = compose.screenshot("progress_month", false, dock = true) { ProgressScreen(state(ProgressRange.Month), null, {}, {}) }
    @Test fun empty() = compose.screenshot("progress_empty", false, dock = true) {
        ProgressScreen(ProgressUiState(ProgressRange.Week, ProgressSummary.of(emptyList(), today.minusDays(6), today, zone), emptyList(), null, Goals()), null, {}, {})
    }
}
