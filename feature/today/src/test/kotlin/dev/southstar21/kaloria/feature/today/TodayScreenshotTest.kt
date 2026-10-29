package dev.southstar21.kaloria.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import dev.southstar21.kaloria.core.health.StepsAccess
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.model.WaterEntry
import dev.southstar21.kaloria.core.model.sum
import dev.southstar21.kaloria.core.testing.screenshot
import dev.southstar21.kaloria.core.ui.PortionSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TodayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 29)

    private fun entry(id: String, name: String, brand: String?, hour: Int, grams: Double, per100: Nutrients) = LogEntry(
        id = id, foodId = null, foodName = name, brand = brand, amount = grams, unit = AmountUnit.Gram,
        nutrientsPer100 = per100, loggedAt = day.atTime(hour, 15).atZone(ZoneId.systemDefault()).toInstant(),
    )

    private val entries = listOf(
        entry("a", "Zabpehely", "Gyermelyi", 8, 60.0, Nutrients(energyKcal = 372.0, protein = 13.5, carbohydrates = 58.7, fat = 7.0, fiber = 10.0, micros = mapOf(Micronutrient.Iron to 4.0))),
        entry("b", "Trappista sajt, félzsíros, szeletelt, nagyon hosszú név", "Pannontej", 12, 45.0, Nutrients(energyKcal = 320.0, protein = 24.0, carbohydrates = 2.0, fat = 24.0, salt = 1.8)),
        entry("c", "Chicken breast, raw", null, 19, 200.0, Nutrients(energyKcal = 120.0, protein = 22.5, carbohydrates = 0.0, fat = 2.6)),
    )

    private val goals = Goals(energyKcal = 2200, protein = 150, carbohydrates = 230, fat = 75, fiber = 30, waterMl = 2500, steps = 9000)

    private val full = TodayUiState(
        date = day, today = day, entries = entries, totals = entries.map { it.nutrients }.sum(),
        water = listOf(WaterEntry("w", 1250, day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant())),
        goals = goals, loading = false,
    )

    private fun render(name: String, dark: Boolean, state: TodayUiState, steps: StepsState) =
        compose.screenshot(name, dark, dock = true) {
            TodayScreen(
                state = state, steps = steps, onConnectSteps = {},
                onPreviousDay = {}, onNextDay = {}, onToday = {}, onAddWater = {}, onUndoWater = {},
                onUpdateEntry = { _, _, _ -> }, onDeleteEntry = {},
            )
        }

    @Test fun full_light() = render("today", false, full, StepsState(StepsAccess.Granted, 6420))
    @Test fun full_dark() = render("today", true, full, StepsState(StepsAccess.Granted, 6420))

    @Test fun empty_light() = render("today_empty", false, TodayUiState(day, day, loading = false), StepsState(StepsAccess.NeedsPermission, null))
    @Test fun empty_dark() = render("today_empty", true, TodayUiState(day, day, loading = false), StepsState(StepsAccess.NeedsPermission, null))

    @Test fun over_goal_past_day() = render(
        "today_over_past", false,
        full.copy(date = day.minusDays(3), goals = goals.copy(energyKcal = 500)),
        StepsState(StepsAccess.Granted, 12000),
    )

    private fun sheet(dark: Boolean) = compose.screenshot("portion_sheet", dark, screen = true) {
        PortionSheet(
            title = "Trappista sajt", subtitle = "Pannontej · Open Food Facts", unit = AmountUnit.Gram,
            per100 = Nutrients(energyKcal = 320.0, protein = 24.0, carbohydrates = 2.0, fat = 24.0),
            servings = listOf(Serving("1 slice", 14.0), Serving("1 serving (30 g)", 30.0)),
            initialAmount = 45.0, initialTime = LocalTime.of(12, 15), confirmLabel = "Save",
            onConfirm = { _, _ -> }, onDismiss = {}, onDelete = {},
        )
    }

    @Test fun sheet_light() = sheet(false)
    @Test fun sheet_dark() = sheet(true)
}
