package dev.southstar21.kaloria.feature.foodsearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodCatalog
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.OnlineResults
import dev.southstar21.kaloria.core.data.repository.logFood
import dev.southstar21.kaloria.core.data.repository.saveServings
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.RemoteFood
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OnlineState {
    /** Query too short to bother the APIs. */
    data object Idle : OnlineState
    data object Loading : OnlineState
    data class Loaded(val results: OnlineResults) : OnlineState
}

/** Something the user can log: a food they own, or one found online (imported on first log). */
sealed interface Pickable {
    val key: String

    data class Own(val food: Food) : Pickable {
        override val key get() = "own:${food.id}"
    }

    data class Online(val food: RemoteFood) : Pickable {
        override val key get() = "${food.source}:${food.sourceRef}"
    }
}

@OptIn(FlowPreview::class, SavedStateHandleSaveableApi::class)
@HiltViewModel(assistedFactory = FoodSearchViewModel.Factory::class)
class FoodSearchViewModel @AssistedInject constructor(
    @Assisted private val key: FoodSearchKey,
    private val savedState: SavedStateHandle,
    private val foods: FoodRepository,
    private val catalog: FoodCatalog,
    private val diary: DiaryRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: FoodSearchKey): FoodSearchViewModel
    }

    /** The day foods are logged onto, worked out when logging: "today" moves on at midnight. */
    fun day(): LocalDate = key.epochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now(clock)

    // Saved so a restored portion sheet finds the same results again.
    var query by savedState.saveable { mutableStateOf("") }

    /** Which food's portion sheet is open; saved, so the sheet comes back after process death. */
    var pickedKey by mutableStateOf(savedState.get<String>(PICKED_KEY))
        private set

    /**
     * The picked food itself, so its sheet stays open while the online results it came from are
     * reloaded or lost (they're dropped a few seconds after the screen goes to the background).
     */
    private var picked by mutableStateOf<Pickable?>(null)

    fun pick(pick: Pickable?) {
        picked = pick
        pickedKey = pick?.key
        savedState[PICKED_KEY] = pickedKey
    }

    /**
     * The food whose sheet is open: the user's own food as it is now (its servings may have just
     * changed), else the one picked, else after process death the same result found again.
     */
    fun pickIn(results: List<Food>?, online: OnlineState): Pickable? {
        val key = pickedKey ?: return null
        return results.orEmpty().map(Pickable::Own).firstOrNull { it.key == key }
            ?: picked?.takeIf { it.key == key }
            ?: (online as? OnlineState.Loaded)?.results?.let { it.openFoodFacts + it.usda }.orEmpty().map(Pickable::Online).firstOrNull { it.key == key }
    }

    private val trimmedQuery = snapshotFlow { query.trim() }.distinctUntilChanged()

    val results: StateFlow<List<Food>?> = trimmedQuery
        .debounce(150)
        .flatMapLatest { foods.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The last complete online answer, so coming back to the screen doesn't search again for the same query. */
    private var lastOnline: Pair<String, OnlineResults>? = null

    val online: StateFlow<OnlineState> = trimmedQuery
        .debounce(450)
        .flatMapLatest { q ->
            if (q.length < MIN_ONLINE_QUERY) {
                flow { emit(OnlineState.Idle) }
            } else {
                flow {
                    val cached = lastOnline?.takeIf { it.first == q }?.second
                    if (cached != null) {
                        emit(OnlineState.Loaded(cached))
                    } else {
                        emit(OnlineState.Loading)
                        val results = catalog.searchOnline(q).withoutDuplicates()
                        // A failed source is worth asking again next time.
                        if (results.failed.isEmpty()) lastOnline = q to results
                        emit(OnlineState.Loaded(results))
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnlineState.Idle)

    private val loggedChannel = Channel<String>(Channel.BUFFERED)

    /** Name of each food once it's been logged, for a confirmation snackbar. */
    val logged = loggedChannel.receiveAsFlow()

    fun defaultTime(): LocalTime = if (day() == LocalDate.now(clock)) LocalTime.now(clock).withSecond(0).withNano(0) else LocalTime.NOON

    fun saveServings(foodId: String, servings: List<dev.southstar21.kaloria.core.model.Serving>) = viewModelScope.launch { foods.saveServings(foodId, servings) }

    private var logging: Job? = null

    /** Ignored while a log is still being written, so a double tap logs once. */
    fun log(pick: Pickable, amount: Double, time: LocalTime) {
        if (logging?.isActive == true) return
        logging = viewModelScope.launch {
            val food = when (pick) {
                is Pickable.Own -> pick.food
                is Pickable.Online -> catalog.import(pick.food)
            }
            diary.logFood(food, amount, day().atTime(time).atZone(clock.zone).toInstant())
            loggedChannel.send(food.name)
        }
    }

    private companion object {
        const val MIN_ONLINE_QUERY = 3
        const val PICKED_KEY = "picked"
    }
}

/** Each source's results with repeats of the same product dropped; the list is keyed by them. */
internal fun OnlineResults.withoutDuplicates() = copy(
    openFoodFacts = openFoodFacts.distinctBy { it.sourceRef },
    usda = usda.distinctBy { it.sourceRef },
)
