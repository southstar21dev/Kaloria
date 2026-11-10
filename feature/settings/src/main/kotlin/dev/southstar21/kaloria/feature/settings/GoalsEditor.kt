package dev.southstar21.kaloria.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.atwaterKcal
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Goals as typed; blank means "no target". */
data class GoalsForm(
    val energyKcal: String = "",
    val protein: String = "",
    val carbohydrates: String = "",
    val fat: String = "",
    val fiber: String = "",
    val steps: String = "",
) {
    /**
     * [previous] with only the fields typed differently from [base] (the form as it was loaded)
     * replaced. Goals that arrived from a sync while this form was open are kept, not overwritten
     * with what the form showed before.
     */
    fun toGoals(previous: Goals, base: GoalsForm) = previous.copy(
        energyKcal = changedOr(energyKcal, base.energyKcal, previous.energyKcal),
        protein = changedOr(protein, base.protein, previous.protein),
        carbohydrates = changedOr(carbohydrates, base.carbohydrates, previous.carbohydrates),
        fat = changedOr(fat, base.fat, previous.fat),
        fiber = changedOr(fiber, base.fiber, previous.fiber),
        steps = changedOr(steps, base.steps, previous.steps),
    )

    /**
     * This form moved from [oldBase] onto [newBase]: fields the user has changed keep what they
     * typed, the rest show the new values. With nothing changed it's simply [newBase].
     */
    fun rebase(oldBase: GoalsForm, newBase: GoalsForm) = GoalsForm(
        energyKcal = if (energyKcal == oldBase.energyKcal) newBase.energyKcal else energyKcal,
        protein = if (protein == oldBase.protein) newBase.protein else protein,
        carbohydrates = if (carbohydrates == oldBase.carbohydrates) newBase.carbohydrates else carbohydrates,
        fat = if (fat == oldBase.fat) newBase.fat else fat,
        fiber = if (fiber == oldBase.fiber) newBase.fiber else fiber,
        steps = if (steps == oldBase.steps) newBase.steps else steps,
    )

    /** A goal of 0 can't be met by anything eaten; empty is how to say "no target". */
    val hasZero: Boolean get() = toArray().any { it.toIntOrNull() == 0 }

    /** What the macro targets add up to, to compare with the energy target. Fiber isn't counted: it's a minimum, not a share. */
    val macroKcal: Int?
        get() {
            val p = protein.toIntOrNull() ?: return null
            val c = carbohydrates.toIntOrNull() ?: return null
            val f = fat.toIntOrNull() ?: return null
            return atwaterKcal(p.toDouble(), c.toDouble(), f.toDouble()).roundToInt()
        }

    /** The fields in order, for saving a draft in a [SavedStateHandle]. */
    fun toArray() = arrayOf(energyKcal, protein, carbohydrates, fat, fiber, steps)

    companion object {
        fun fromArray(fields: Array<String>) = if (fields.size == 6) GoalsForm(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]) else null

        fun from(g: Goals) = GoalsForm(
            energyKcal = g.energyKcal?.toString().orEmpty(),
            protein = g.protein?.toString().orEmpty(),
            carbohydrates = g.carbohydrates?.toString().orEmpty(),
            fat = g.fat?.toString().orEmpty(),
            fiber = g.fiber?.toString().orEmpty(),
            steps = g.steps?.toString().orEmpty(),
        )
    }
}

/** The goals as typed ([form]) and as stored ([saved]), which is also what a change is measured from. */
data class GoalsEditorState(val form: GoalsForm = GoalsForm(), val saved: GoalsForm = GoalsForm()) {
    val changed get() = form != saved
    val valid get() = !form.hasZero

    /** Follows stored goals that changed (a sync), keeping only the user's own edits. */
    fun rebasedOn(stored: GoalsForm) = GoalsEditorState(form.rebase(saved, stored), stored)
}

private fun changedOr(typed: String, base: String, previous: Int?) = if (typed == base) previous else typed.toIntOrNull()

/**
 * The daily goals form in Settings. It follows the stored goals for as long as it lives, so goals
 * a sync brings in while it's open show up, and a save writes only the fields the user changed on
 * top of the latest stored goals.
 */
class GoalsEditor(
    private val repository: GoalsRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
) {
    /** Starts from unsaved edits from before the process was killed, if any, with the goals they were edits of. */
    var state by mutableStateOf(
        savedState.get<Array<String>>(DRAFT)?.let(GoalsForm::fromArray)?.let { draft ->
            GoalsEditorState(draft, savedState.get<Array<String>>(BASE)?.let(GoalsForm::fromArray) ?: GoalsForm())
        } ?: GoalsEditorState(),
    )
        private set

    private var current = Goals()

    init {
        scope.launch {
            repository.goals.collect { stored ->
                current = stored
                state = state.rebasedOn(GoalsForm.from(stored))
                saveDraft()
            }
        }
    }

    fun edit(transform: GoalsForm.() -> GoalsForm) {
        state = state.copy(form = state.form.transform())
        saveDraft()
    }

    fun save() = scope.launch {
        val editing = state
        if (!editing.changed || !editing.valid) return@launch
        val updated = editing.form.toGoals(current, editing.saved)
        repository.save(updated)
        current = updated
        state = state.copy(saved = editing.form)
        saveDraft()
    }

    /** Keeps unsaved edits, and what they're edits of, across process death. */
    private fun saveDraft() {
        if (state.changed) {
            savedState[DRAFT] = state.form.toArray()
            savedState[BASE] = state.saved.toArray()
        } else {
            savedState.remove<Array<String>>(DRAFT)
            savedState.remove<Array<String>>(BASE)
        }
    }

    private companion object {
        const val DRAFT = "goals_draft"
        const val BASE = "goals_base"
    }
}
