package dev.southstar21.kaloria.feature.progress

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.di.DefaultDispatcher
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProgressRange(val days: Long) { Week(7), Month(30), Quarter(90) }

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
    @DefaultDispatcher private val compute: CoroutineDispatcher,
) : ViewModel() {

    private val _range = MutableStateFlow(ProgressRange.Week)

    /** The range shown; change it with [setRange], which also refreshes the steps. */
    val range: StateFlow<ProgressRange> = _range.asStateFlow()

    /** The current day, checked on resume and as the clock ticks, so the range and the streak move on past midnight. */
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** The streak doesn't depend on the range, so switching range doesn't re-read every logged day. */
    private val activity = combine(diary.loggedDays(), today) { days, today ->
        val gridStart = today.minusWeeks(ACTIVITY_WEEKS.toLong())
        Triple(today, Streak.of(days, today), days.filterTo(HashSet()) { it > gridStart })
    }.flowOn(compute)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val state: StateFlow<ProgressUiState?> = combine(_range, today, ::Pair).flatMapLatest { (r, today) ->
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
    }.flowOn(compute) // Summarising up to 90 days of entries is too much for the main thread.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Per day of the current range, null if Health Connect isn't connected. */
    var steps by mutableStateOf<List<Long?>?>(null)
        private set

    /** Whether weight syncs with Health Connect (and so Samsung Health); null until first checked. */
    val weightAccess: StateFlow<HealthAccess?> = weightSync.accessState

    /** True while a Health Connect weight sync is running. */
    val syncingWeight: StateFlow<Boolean> = weightSync.syncing

    /** True when the last weight sync with Health Connect failed; the next one tries again. */
    val weightSyncFailed: StateFlow<Boolean> = weightSync.failed

    val healthPermissions get() = weightSync.permissions + stepsRepository.permissions
    fun healthPermissionContract() = weightSync.permissionContract()

    private var stepsJob: Job? = null

    /** On resume: steps for the range, and a weight sync both ways unless one ran just now. */
    fun refresh() {
        refreshSteps()
        viewModelScope.launch { weightSync.syncIfStale() }
    }

    /**
     * Replaces any refresh still in flight, so a slow, stale answer can't overwrite a newer one.
     * Moves "today" on first, so the steps and the range's summary cover the same days.
     */
    fun refreshSteps(): Job {
        stepsJob?.cancel()
        val end = LocalDate.now(clock).also { today.value = it }
        return viewModelScope.launch {
            if (stepsRepository.access() != HealthAccess.Granted) {
                steps = null
                return@launch
            }
            val from = end.minusDays(_range.value.days - 1)
            val perDay = stepsRepository.stepsPerDay(from, end)
            steps = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { perDay[it] }.toList()
        }.also { stepsJob = it }
    }

    /** Called as the clock ticks while the screen is in front: at midnight, the range moves on to the new day. */
    fun onTimeChange() {
        if (LocalDate.now(clock) != today.value) refreshSteps()
    }

    fun syncWeights() = viewModelScope.launch { weightSync.sync() }

    fun setRange(r: ProgressRange) {
        _range.value = r
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
