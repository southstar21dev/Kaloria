package dev.southstar21.kaloria.feature.scan.barcode

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.BarcodeResult
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodCatalog
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.logFood
import dev.southstar21.kaloria.core.data.repository.saveServings
import dev.southstar21.kaloria.feature.scan.BarcodeScanKey
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface BarcodeState {
    data object Scanning : BarcodeState
    data class LookingUp(val code: String) : BarcodeState
    data class Found(val code: String, val result: BarcodeResult) : BarcodeState
}

@HiltViewModel(assistedFactory = BarcodeScanViewModel.Factory::class)
class BarcodeScanViewModel @AssistedInject constructor(
    @Assisted key: BarcodeScanKey,
    private val catalog: FoodCatalog,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: BarcodeScanKey): BarcodeScanViewModel
    }

    private val day = LocalDate.ofEpochDay(key.epochDay)

    var state by mutableStateOf<BarcodeState>(BarcodeState.Scanning)
        private set

    private val doneChannel = Channel<String>(Channel.CONFLATED)

    /** The logged food's name, after which the screen closes. */
    val done = doneChannel.receiveAsFlow()

    /** Whether the found food is being logged, so a second tap on Log doesn't log it twice. */
    var logging by mutableStateOf(false)
        private set

    /** Called on the main thread (ML Kit's listeners run there) for every confirmed read; only the first counts. */
    fun onBarcode(code: String) {
        if (state != BarcodeState.Scanning) return
        state = BarcodeState.LookingUp(code)
        viewModelScope.launch { state = BarcodeState.Found(code, catalog.lookupBarcode(code)) }
    }

    fun saveServings(foodId: String, servings: List<dev.southstar21.kaloria.core.model.Serving>) = viewModelScope.launch { foods.saveServings(foodId, servings) }

    fun rescan() {
        state = BarcodeState.Scanning
    }

    fun defaultTime(): LocalTime = if (day == LocalDate.now(clock)) LocalTime.now(clock).withSecond(0).withNano(0) else LocalTime.NOON

    fun log(amount: Double, time: LocalTime) {
        if (logging) return
        val found = (state as? BarcodeState.Found)?.result ?: return
        logging = true
        viewModelScope.launch {
            var sent = false
            try {
                val food = when (found) {
                    is BarcodeResult.Own -> found.food
                    is BarcodeResult.Online -> catalog.import(found.food)
                    else -> return@launch
                }
                diary.logFood(food, amount, day.atTime(time).atZone(clock.zone).toInstant())
                doneChannel.send(food.name)
                sent = true
            } finally {
                // Stays set once logged: the screen is closing, and must not log again on the way out.
                if (!sent) logging = false
            }
        }
    }
}
