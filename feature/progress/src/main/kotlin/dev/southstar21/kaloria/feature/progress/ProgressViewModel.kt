package dev.southstar21.kaloria.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.health.StepsAccess
import dev.southstar21.kaloria.core.health.StepsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.WeightEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class ProgressRange(val days: Long, val label: String) { Week(7, "7 days"), Month(30, "30 days"), Quarter(90, "90 days") }

data class ProgressUiState(
    val range: ProgressRange,
    val summary: ProgressSummary,
    val weights: List<WeightEntry>,
    val latestWeight: WeightEntry?,
    val goals: Goals,
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val diary: DiaryRepository,
    goals: GoalsRepository,
    private val stepsRepository: StepsRepository,
    private val clock: Clock,
) : ViewModel() {

    val range = MutableStateFlow(ProgressRange.Week)

    val state: StateFlow<ProgressUiState?> = range.flatMapLatest { r ->
        val today = LocalDate.now(clock)
        val from = today.minusDays(r.days - 1)
        combine(diary.entries(from, today), diary.weights(today.minusDays(maxOf(r.days, 90) - 1), today), diary.latestWeight(), goals.goals) { entries, weights, latest, g ->
            ProgressUiState(r, ProgressSummary.of(entries, from, today, clock.zone), weights, latest, g)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Per day of the current range, null if Health Connect isn't connected. */
    var steps by mutableStateOf<List<Long?>?>(null)
        private set

    fun refreshSteps() = viewModelScope.launch {
        if (stepsRepository.access() != StepsAccess.Granted) {
            steps = null
            return@launch
        }
        val today = LocalDate.now(clock)
        val from = today.minusDays(range.value.days - 1)
        val perDay = stepsRepository.stepsPerDay(from, today)
        steps = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { perDay[it] }.toList()
    }

    fun setRange(r: ProgressRange) {
        range.value = r
        refreshSteps()
    }

    fun logWeight(kilograms: Double) = viewModelScope.launch {
        diary.addWeight(kilograms, LocalDate.now(clock).atTime(LocalTime.now(clock)).atZone(clock.zone).toInstant())
    }

    fun deleteWeight(id: String) = viewModelScope.launch { diary.deleteWeight(id) }
}
