package dev.southstar21.kaloria.feature.foodsearch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodCatalog
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.OnlineResults
import dev.southstar21.kaloria.core.data.repository.logFood
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.RemoteFood
import kotlinx.coroutines.FlowPreview
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
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

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

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = FoodSearchViewModel.Factory::class)
class FoodSearchViewModel @AssistedInject constructor(
    @Assisted private val key: FoodSearchKey,
    private val foods: FoodRepository,
    private val catalog: FoodCatalog,
    private val diary: DiaryRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: FoodSearchKey): FoodSearchViewModel
    }

    val day: LocalDate = LocalDate.ofEpochDay(key.epochDay)

    var query by mutableStateOf("")

    private val trimmedQuery = snapshotFlow { query.trim() }.distinctUntilChanged()

    val results: StateFlow<List<Food>?> = trimmedQuery
        .debounce(150)
        .flatMapLatest { foods.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val online: StateFlow<OnlineState> = trimmedQuery
        .debounce(450)
        .flatMapLatest { q ->
            if (q.length < MIN_ONLINE_QUERY) {
                flow { emit(OnlineState.Idle) }
            } else {
                flow {
                    emit(OnlineState.Loading)
                    emit(OnlineState.Loaded(catalog.searchOnline(q)))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnlineState.Idle)

    private val loggedChannel = Channel<String>(Channel.BUFFERED)

    /** Name of each food once it's been logged, for a confirmation snackbar. */
    val logged = loggedChannel.receiveAsFlow()

    fun defaultTime(): LocalTime =
        if (day == LocalDate.now(clock)) LocalTime.now(clock).withSecond(0).withNano(0) else LocalTime.NOON

    fun log(pick: Pickable, amount: Double, time: LocalTime) = viewModelScope.launch {
        val food = when (pick) {
            is Pickable.Own -> pick.food
            is Pickable.Online -> catalog.import(pick.food)
        }
        diary.logFood(food, amount, day.atTime(time).atZone(clock.zone).toInstant())
        loggedChannel.send(food.name)
    }

    private companion object {
        const val MIN_ONLINE_QUERY = 3
    }
}
