package dev.southstar21.kaloria.feature.foods.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Ids
import dev.southstar21.kaloria.core.scanner.label.ExtractionMethod
import dev.southstar21.kaloria.core.scanner.label.ScanDraftStore
import dev.southstar21.kaloria.feature.foods.FoodEditorKey
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class FoodEditorState(
    val form: FoodForm = FoodForm(),
    val existing: Food? = null,
    val loading: Boolean = true,
    /** Set once the user tried to save, so errors aren't shown on a fresh form. */
    val showErrors: Boolean = false,
    /** Present when reviewing a scanned label. */
    val scan: ScanReview? = null,
    /** Form as first shown, to ask before discarding edits. */
    val initial: FoodForm = form,
    val saveAttempts: Int = 0,
) {
    val changed: Boolean get() = scan != null || form != initial
}

data class ScanReview(
    val photo: File?,
    val method: ExtractionMethod,
    /** Field name (energy, protein, …) → why to double-check it. */
    val warnings: Map<String, List<String>>,
)

@HiltViewModel(assistedFactory = FoodEditorViewModel.Factory::class)
class FoodEditorViewModel @AssistedInject constructor(
    @Assisted private val key: FoodEditorKey,
    private val foods: FoodRepository,
    private val drafts: ScanDraftStore,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: FoodEditorKey): FoodEditorViewModel
    }

    // Snapshot state rather than a StateFlow: text fields must read back what was typed in the same
    // frame, or keystrokes that land during a slow frame get overwritten.
    var state by mutableStateOf(FoodEditorState())
        private set

    private val doneChannel = Channel<Unit>(Channel.CONFLATED)
    val done = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = key.foodId?.let { foods.get(it) }
            val draft = key.draftId?.let { drafts[it] }
            val form = when {
                existing != null -> FoodForm.from(existing)
                draft != null -> FoodForm.from(draft.extraction, draft.barcode ?: key.barcode)
                else -> FoodForm(name = key.name.orEmpty(), barcode = key.barcode.orEmpty())
            }
            state = FoodEditorState(
                form = form,
                initial = form,
                existing = existing,
                loading = false,
                scan = draft?.let { d ->
                    ScanReview(d.photo, d.extraction.method, d.extraction.warnings.groupBy({ it.field }, { it.message }))
                },
            )
        }
    }

    fun edit(transform: FoodForm.() -> FoodForm) {
        state = state.copy(form = state.form.transform())
    }

    fun save() {
        val current = state
        if (current.form.errors.any) {
            state = current.copy(showErrors = true, saveAttempts = current.saveAttempts + 1)
            return
        }
        viewModelScope.launch {
            val existing = current.existing
            foods.save(
                current.form.toFood(
                    id = existing?.id ?: Ids.newId(),
                    source = existing?.source ?: if (current.scan != null) FoodSource.Label else FoodSource.Manual,
                    sourceRef = existing?.sourceRef,
                ),
            )
            key.draftId?.let(drafts::remove)
            doneChannel.send(Unit)
        }
    }

    override fun onCleared() {
        // Leaving without saving discards the scan; its photo is only kept for the review.
        key.draftId?.let(drafts::remove)
    }

    fun delete() {
        val id = state.existing?.id ?: return
        viewModelScope.launch {
            foods.delete(id)
            doneChannel.send(Unit)
        }
    }
}
