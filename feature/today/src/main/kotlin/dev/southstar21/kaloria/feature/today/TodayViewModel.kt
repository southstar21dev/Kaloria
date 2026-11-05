package dev.southstar21.kaloria.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.di.DefaultDispatcher
import dev.southstar21.kaloria.core.data.health.StepsSource
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.data.repository.saveServings
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.sum
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val entries: List<LogEntry> = emptyList(),
    val totals: Nutrients = Nutrients.Empty,
    val goals: Goals = Goals(),
    val loading: Boolean = true,
) {
    val stepsGoal: Int? get() = goals.steps
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val diary: DiaryRepository,
    goalsRepository: GoalsRepository,
    private val foods: FoodRepository,
    private val stepsRepository: StepsSource,
    private val clock: Clock,
    @DefaultDispatcher private val compute: CoroutineDispatcher,
) : ViewModel() {

    /** Steps for the selected day; Health Connect isn't observable, so it's refreshed on resume. */
    var steps by mutableStateOf<StepsState>(StepsState(HealthAccess.Unsupported, null))
        private set

    val stepsPermissions get() = stepsRepository.permissions
    fun stepsPermissionContract() = stepsRepository.permissionContract()

    /** The day "today" was when last seen; refreshed on resume, to follow the date past midnight. */
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** Called on resume: if the app sat open past midnight, move from yesterday to the new today. */
    fun onResume() {
        val now = LocalDate.now(clock)
        val lastToday = today.value
        if (now != lastToday) {
            if (date.value == lastToday) date.value = now
            today.value = now
        }
        refreshSteps()
    }

    suspend fun servingsFor(entry: LogEntry): List<dev.southstar21.kaloria.core.model.Serving> = entry.foodId?.let { foods.get(it)?.servings }.orEmpty()

    private var stepsJob: Job? = null

    /** Replaces any refresh still in flight: swiping through days shouldn't queue Health Connect calls. */
    fun refreshSteps(): Job {
        stepsJob?.cancel()
        return viewModelScope.launch {
            val access = stepsRepository.access()
            steps = StepsState(access, if (access == HealthAccess.Granted) stepsRepository.steps(date.value) ?: 0L else null)
        }.also { stepsJob = it }
    }

    private val date = MutableStateFlow(LocalDate.now(clock))

    val uiState: StateFlow<TodayUiState> = date.flatMapLatest { day ->
        combine(diary.entries(day), goalsRepository.goals, today) { entries, goals, today ->
            TodayUiState(
                date = day,
                today = today,
                entries = entries,
                totals = entries.map { it.nutrients }.sum(),
                goals = goals,
                loading = false,
            )
        }
    }.flowOn(compute).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date.value, today.value))

    val selectedDate: StateFlow<LocalDate> = date

    fun previousDay() {
        date.update { it.minusDays(1) }
        refreshSteps()
    }
    fun nextDay() {
        date.update { it.plusDays(1) }
        refreshSteps()
    }
    fun goToToday() {
        date.value = LocalDate.now(clock)
        refreshSteps()
    }
    fun selectDay(day: LocalDate) {
        if (day != date.value) {
            date.value = day
            refreshSteps()
        }
    }

    fun updateEntry(entry: LogEntry, amount: Double, time: LocalTime) = viewModelScope.launch {
        val day = entry.loggedAt.atZone(clock.zone).toLocalDate()
        diary.saveEntry(entry.copy(amount = amount, loggedAt = day.atTime(time).atZone(clock.zone).toInstant()))
    }

    fun deleteEntry(entry: LogEntry) = viewModelScope.launch { diary.deleteEntry(entry.id) }

    fun saveServings(foodId: String, servings: List<dev.southstar21.kaloria.core.model.Serving>) = viewModelScope.launch { foods.saveServings(foodId, servings) }
}

data class StepsState(val access: HealthAccess, val count: Long?)
