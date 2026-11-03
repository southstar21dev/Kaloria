package dev.southstar21.kaloria.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.health.HealthWeightSync
import dev.southstar21.kaloria.core.data.health.StepsSource
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.WeightEntry
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProgressRange(val days: Long, val label: String) { Week(7, "7 days"), Month(30, "30 days"), Quarter(90, "90 days") }

data class ProgressUiState(
    val range: ProgressRange,
    val summary: ProgressSummary,
    /** Weigh-ins in the range, oldest first. */
    val weights: List<WeightEntry>,
    val latestWeight: WeightEntry?,
    val goals: Goals,
    val streak: Streak,
    /** Days with anything logged over the last [ACTIVITY_WEEKS] weeks, for the activity grid. */
    val recentDays: Set<LocalDate>,
    val today: LocalDate,
)

/** How many weeks the streak tile's activity grid shows. */
internal const val ACTIVITY_WEEKS = 26

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val diary: DiaryRepository,
    goals: GoalsRepository,
    private val stepsRepository: StepsSource,
    private val weightSync: HealthWeightSync,
    private val clock: Clock,
) : ViewModel() {

    val range = MutableStateFlow(ProgressRange.Week)

    /** The streak doesn't depend on the range, so switching range doesn't re-read every logged day. */
    private val activity = diary.loggedDays().map { days ->
        val today = LocalDate.now(clock)
        val gridStart = today.minusWeeks(ACTIVITY_WEEKS.toLong())
        Triple(today, Streak.of(days, today), days.filterTo(HashSet()) { it > gridStart })
    }.flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val state: StateFlow<ProgressUiState?> = range.flatMapLatest { r ->
        val today = LocalDate.now(clock)
        val from = today.minusDays(r.days - 1)
        combine(diary.entries(from, today), diary.weights(from, today), diary.latestWeight(), goals.goals, activity) { entries, weights, latest, g, (_, streak, recent) ->
            ProgressUiState(
                range = r,
                summary = ProgressSummary.of(entries, from, today, clock.zone),
                weights = weights,
                latestWeight = latest,
                goals = g,
                streak = streak,
                recentDays = recent,
                today = today,
            )
        }
    }.flowOn(Dispatchers.Default) // Summarising up to 90 days of entries is too much for the main thread.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Per day of the current range, null if Health Connect isn't connected. */
    var steps by mutableStateOf<List<Long?>?>(null)
        private set

    /** Whether weight syncs with Health Connect (and so Samsung Health); null until first checked. */
    val weightAccess: StateFlow<HealthAccess?> = weightSync.accessState

    /** True while a Health Connect weight sync is running. */
    val syncingWeight: StateFlow<Boolean> = weightSync.syncing

    val healthPermissions get() = weightSync.permissions + stepsRepository.permissions
    fun healthPermissionContract() = weightSync.permissionContract()

    private var stepsJob: Job? = null

    /** On resume: steps for the range, and a weight sync both ways unless one ran just now. */
    fun refresh() {
        refreshSteps()
        viewModelScope.launch { weightSync.syncIfStale() }
    }

    /** Replaces any refresh still in flight, so a slow, stale answer can't overwrite a newer one. */
    fun refreshSteps(): Job {
        stepsJob?.cancel()
        return viewModelScope.launch {
            if (stepsRepository.access() != HealthAccess.Granted) {
                steps = null
                return@launch
            }
            val today = LocalDate.now(clock)
            val from = today.minusDays(range.value.days - 1)
            val perDay = stepsRepository.stepsPerDay(from, today)
            steps = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { perDay[it] }.toList()
        }.also { stepsJob = it }
    }

    fun syncWeights() = viewModelScope.launch { weightSync.sync() }

    fun setRange(r: ProgressRange) {
        range.value = r
        refreshSteps()
    }

    fun logWeight(kilograms: Double) = viewModelScope.launch {
        diary.addWeight(kilograms, LocalDate.now(clock).atTime(LocalTime.now(clock)).atZone(clock.zone).toInstant())
        syncWeights()
    }

    fun deleteWeight(id: String) = viewModelScope.launch {
        diary.deleteWeight(id)
        syncWeights()
    }
}
