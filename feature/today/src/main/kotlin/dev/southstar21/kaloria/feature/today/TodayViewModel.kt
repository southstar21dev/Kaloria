package dev.southstar21.kaloria.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.WaterEntry
import dev.southstar21.kaloria.core.model.sum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.southstar21.kaloria.core.health.StepsAccess
import dev.southstar21.kaloria.core.health.StepsRepository

data class TodayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val entries: List<LogEntry> = emptyList(),
    val totals: Nutrients = Nutrients.Empty,
    val water: List<WaterEntry> = emptyList(),
    val goals: Goals = Goals(),
    val loading: Boolean = true,
) {
    val stepsGoal: Int? get() = goals.steps
    val waterMl: Int get() = water.sumOf { it.millilitres }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val diary: DiaryRepository,
    goalsRepository: GoalsRepository,
    private val foods: FoodRepository,
    private val stepsRepository: StepsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Steps for the selected day; Health Connect isn't observable, so it's refreshed on resume. */
    var steps by mutableStateOf<StepsState>(StepsState(StepsAccess.Unsupported, null))
        private set

    val stepsPermissions get() = stepsRepository.permissions
    fun stepsPermissionContract() = stepsRepository.permissionContract()

    /** The day "today" was when last seen, to follow the date past midnight. */
    private var lastToday = LocalDate.now(clock)

    /** Called on resume: if the app sat open past midnight, move from yesterday to the new today. */
    fun onResume() {
        val now = LocalDate.now(clock)
        if (now != lastToday) {
            if (date.value == lastToday) date.value = now
            lastToday = now
        }
        refreshSteps()
    }

    suspend fun servingsFor(entry: LogEntry): List<dev.southstar21.kaloria.core.model.Serving> =
        entry.foodId?.let { foods.get(it)?.servings }.orEmpty()

    fun refreshSteps() = viewModelScope.launch {
        val access = stepsRepository.access()
        steps = StepsState(access, if (access == StepsAccess.Granted) stepsRepository.steps(date.value) ?: 0L else null)
    }


    private val date = MutableStateFlow(LocalDate.now(clock))

    val uiState: StateFlow<TodayUiState> = date.flatMapLatest { day ->
        combine(diary.entries(day), diary.water(day), goalsRepository.goals) { entries, water, goals ->
            TodayUiState(
                date = day,
                today = LocalDate.now(clock),
                entries = entries,
                totals = entries.map { it.nutrients }.sum(),
                water = water,
                goals = goals,
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date.value, date.value))

    val selectedDate: StateFlow<LocalDate> = date

    fun previousDay() { date.update { it.minusDays(1) }; refreshSteps() }
    fun nextDay() { date.update { it.plusDays(1) }; refreshSteps() }
    fun goToToday() { date.value = LocalDate.now(clock); refreshSteps() }

    fun addWater(millilitres: Int) = viewModelScope.launch {
        diary.addWater(millilitres, timestampOn(date.value))
    }

    fun undoWater() = viewModelScope.launch {
        uiState.value.water.lastOrNull()?.let { diary.deleteWater(it.id) }
    }

    fun updateEntry(entry: LogEntry, amount: Double, time: LocalTime) = viewModelScope.launch {
        val day = entry.loggedAt.atZone(clock.zone).toLocalDate()
        diary.saveEntry(entry.copy(amount = amount, loggedAt = day.atTime(time).atZone(clock.zone).toInstant()))
    }

    fun deleteEntry(entry: LogEntry) = viewModelScope.launch { diary.deleteEntry(entry.id) }

    /** "Now" on today; the current time of day on another day (logging yesterday's forgotten water). */
    private fun timestampOn(day: LocalDate) = day.atTime(LocalTime.now(clock)).atZone(clock.zone).toInstant()
}

data class StepsState(val access: StepsAccess, val count: Long?)
