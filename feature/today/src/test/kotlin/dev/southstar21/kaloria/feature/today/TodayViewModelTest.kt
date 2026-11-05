package dev.southstar21.kaloria.feature.today

import androidx.lifecycle.ViewModelStore
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TodayViewModelTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val today = LocalDate.of(2026, 10, 30)
    private val yesterday = today.minusDays(1)
    private val clock = MutableClock(local("2026-10-30T12:00"), zone)

    private val diary = FakeDiaryRepository(zone)
    private val goals = FakeGoalsRepository(Goals(energyKcal = 2000, steps = 8000))
    private val foods = FakeFoodRepository()
    private val steps = FakeStepsSource(HealthAccess.Granted, mutableMapOf(today to 6420L, yesterday to 1200L))

    private lateinit var viewModel: TodayViewModel

    // One test dispatcher for Main and for the ViewModel's background work, so nothing races.
    private val main = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        diary.entries.value = listOf(
            entry("breakfast", "2026-10-30T08:00", grams = 50.0, kcalPer100 = 380.0),
            entry("lunch", "2026-10-30T12:30", grams = 200.0, kcalPer100 = 120.0),
            entry("dinner-yesterday", "2026-10-29T19:00", grams = 100.0, kcalPer100 = 500.0),
        )
        viewModel = TodayViewModel(diary, goals, foods, steps, clock, compute = main)
    }

    @After
    fun tearDown() {
        ViewModelStore().apply { put("today", viewModel) }.clear()
        Dispatchers.resetMain()
    }

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun entry(id: String, at: String, grams: Double, kcalPer100: Double, foodId: String? = null) = LogEntry(
        id = id,
        foodId = foodId,
        foodName = id,
        brand = null,
        amount = grams,
        unit = AmountUnit.Gram,
        nutrientsPer100 = Nutrients(energyKcal = kcalPer100),
        loggedAt = local(at),
    )

    /** Keeps uiState subscribed (it's WhileSubscribed) and waits for a loaded state matching [predicate]. */
    private suspend fun TestScope.awaitState(predicate: (TodayUiState) -> Boolean = { true }): TodayUiState {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel.uiState.first { !it.loading && predicate(it) }
    }

    @Test
    fun `starts on today with its entries, totals and goals`() = runTest {
        val state = awaitState()

        assertEquals(today, state.date)
        assertEquals(today, state.today)
        assertEquals(listOf("breakfast", "lunch"), state.entries.map { it.id })
        assertEquals(190.0 + 240.0, state.totals.energyKcal!!, 1e-9)
        assertEquals(Goals(energyKcal = 2000, steps = 8000), state.goals)
        assertEquals(8000, state.stepsGoal)
    }

    @Test
    fun `follows changes to the diary and the goals`() = runTest {
        awaitState()

        diary.entries.update { it + entry("snack", "2026-10-30T16:00", grams = 30.0, kcalPer100 = 500.0) }
        goals.goals.value = Goals(energyKcal = 1800)

        val state = awaitState { it.entries.size == 3 && it.goals.energyKcal == 1800 }
        assertEquals(190.0 + 240.0 + 150.0, state.totals.energyKcal!!, 1e-9)
    }

    @Test
    fun `moving between days shows that day's entries and steps`() = runTest {
        viewModel.previousDay()

        val state = awaitState { it.date == yesterday }
        assertEquals(listOf("dinner-yesterday"), state.entries.map { it.id })
        assertEquals(today, state.today)
        assertEquals(yesterday, viewModel.selectedDate.value)
        assertEquals(StepsState(HealthAccess.Granted, 1200L), viewModel.steps)

        viewModel.nextDay()
        assertEquals(listOf("breakfast", "lunch"), awaitState { it.date == today }.entries.map { it.id })
        assertEquals(StepsState(HealthAccess.Granted, 6420L), viewModel.steps)

        viewModel.selectDay(today.minusDays(5))
        assertEquals(emptyList<LogEntry>(), awaitState { it.date == today.minusDays(5) }.entries)

        viewModel.goToToday()
        assertEquals(today, awaitState { it.date == today }.date)
    }

    @Test
    fun `selecting the day already shown doesn't ask Health Connect again`() = runTest {
        viewModel.refreshSteps().join()
        val calls = steps.requestedDays.size

        viewModel.selectDay(today)

        assertEquals(calls, steps.requestedDays.size)
    }

    @Test
    fun `steps without a reading count as zero when granted`() = runTest {
        steps.perDay.clear()

        viewModel.refreshSteps().join()

        assertEquals(StepsState(HealthAccess.Granted, 0L), viewModel.steps)
    }

    @Test
    fun `steps have no count without access`() = runTest {
        steps.access = HealthAccess.NeedsPermission

        viewModel.refreshSteps().join()

        assertEquals(StepsState(HealthAccess.NeedsPermission, null), viewModel.steps)
        assertEquals(emptyList<LocalDate>(), steps.requestedDays)
    }

    @Test
    fun `resuming after midnight moves from the old today to the new one`() = runTest {
        awaitState()
        clock.now = local("2026-10-31T00:05")

        viewModel.onResume()

        val state = awaitState { it.date == today.plusDays(1) }
        assertEquals(today.plusDays(1), state.today)
        assertEquals(today.plusDays(1), viewModel.selectedDate.value)
    }

    @Test
    fun `resuming after midnight leaves an earlier day the user picked alone`() = runTest {
        viewModel.previousDay()
        clock.now = local("2026-10-31T00:05")

        viewModel.onResume()

        assertEquals(yesterday, viewModel.selectedDate.value)
    }

    @Test
    fun `resuming after midnight on an earlier day still moves today on`() = runTest {
        viewModel.previousDay()
        awaitState { it.date == yesterday }
        clock.now = local("2026-10-31T00:05")

        viewModel.onResume()

        val state = awaitState { it.today == today.plusDays(1) }
        assertEquals(yesterday, state.date)
        assertEquals(listOf("dinner-yesterday"), state.entries.map { it.id })
    }

    @Test
    fun `updating an entry keeps its day and changes amount and time`() = runTest {
        val original = diary.entries.value.first { it.id == "dinner-yesterday" }

        viewModel.updateEntry(original, amount = 75.0, time = LocalTime.of(21, 15)).join()

        val saved = diary.entry("dinner-yesterday")!!
        assertEquals(75.0, saved.amount, 0.0)
        assertEquals(local("2026-10-29T21:15"), saved.loggedAt)
    }

    @Test
    fun `deleting an entry goes to the diary`() = runTest {
        viewModel.deleteEntry(diary.entries.value.first()).join()

        assertEquals(listOf("breakfast"), diary.deleted)
    }

    @Test
    fun `servings are read from and saved to the entry's food`() = runTest {
        val food = Food(id = "oats", name = "Oats", source = FoodSource.Manual, nutrients = Nutrients(energyKcal = 380.0), servings = listOf(Serving("cup", 80.0)), updatedAt = Instant.EPOCH)
        foods.foods.value = listOf(food)
        val logged = entry("e", "2026-10-30T08:00", grams = 80.0, kcalPer100 = 380.0, foodId = "oats")

        assertEquals(listOf(Serving("cup", 80.0)), viewModel.servingsFor(logged))
        assertEquals(emptyList<Serving>(), viewModel.servingsFor(logged.copy(foodId = null)))

        viewModel.saveServings("oats", listOf(Serving("bowl", 60.0))).join()

        assertEquals(listOf(Serving("bowl", 60.0)), foods.get("oats")!!.servings)
    }
}
