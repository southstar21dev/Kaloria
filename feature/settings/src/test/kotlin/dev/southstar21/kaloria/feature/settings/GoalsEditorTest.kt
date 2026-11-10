package dev.southstar21.kaloria.feature.settings

import androidx.lifecycle.SavedStateHandle
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.Micronutrient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsEditorTest {
    private val stored = StoredGoals(Goals())
    private val saved = SavedStateHandle()

    private fun TestScope.editor(handle: SavedStateHandle = saved) = GoalsEditor(stored, handle, backgroundScope)

    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = body)

    @Test
    fun `goals a sync brings in show while nothing is being edited`() = test {
        val goals = editor()
        assertEquals(GoalsForm(), goals.state.form)

        stored.goals.value = Goals(energyKcal = 2200, protein = 150)

        assertEquals(GoalsForm(energyKcal = "2200", protein = "150"), goals.state.form)
        assertFalse(goals.state.changed)
    }

    @Test
    fun `saving writes only the fields changed, over goals synced meanwhile`() = test {
        val goals = editor()
        // Opened on a fresh install, then a sign-in pulls the server's goals.
        goals.edit { copy(energyKcal = "2000") }
        stored.goals.value = Goals(energyKcal = 2400, protein = 150, carbohydrates = 230, fat = 70, fiber = 30, steps = 9000, micros = mapOf(Micronutrient.Sodium to 2000.0))

        // The edit survives the sync; the rest of the form follows it.
        assertEquals(GoalsForm("2000", "150", "230", "70", "30", "9000"), goals.state.form)

        goals.save()

        assertEquals(
            Goals(energyKcal = 2000, protein = 150, carbohydrates = 230, fat = 70, fiber = 30, steps = 9000, micros = mapOf(Micronutrient.Sodium to 2000.0)),
            stored.goals.value,
        )
        assertFalse(goals.state.changed)
    }

    @Test
    fun `clearing a field removes that goal`() = test {
        stored.goals.value = Goals(energyKcal = 2200, protein = 150)
        val goals = editor()

        goals.edit { copy(protein = "") }
        goals.save()

        assertEquals(Goals(energyKcal = 2200), stored.goals.value)
    }

    @Test
    fun `a goal of zero can't be saved`() = test {
        stored.goals.value = Goals(energyKcal = 2200)
        val goals = editor()

        goals.edit { copy(energyKcal = "0") }
        assertFalse(goals.state.valid)
        goals.save()

        assertEquals(Goals(energyKcal = 2200), stored.goals.value)
        assertEquals(0, stored.saves)
    }

    @Test
    fun `unsaved edits survive process death and still follow later syncs`() = test {
        stored.goals.value = Goals(energyKcal = 2200, protein = 150)
        editor().edit { copy(energyKcal = "1900") }

        // The process dies; meanwhile another device changes protein.
        stored.goals.value = Goals(energyKcal = 2200, protein = 160)
        val restored = editor(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))

        assertEquals(GoalsForm(energyKcal = "1900", protein = "160"), restored.state.form)
        assertTrue(restored.state.changed)
    }
}

private class StoredGoals(initial: Goals) : GoalsRepository {
    override val goals = MutableStateFlow(initial)
    var saves = 0

    override suspend fun save(goals: Goals) {
        saves++
        this.goals.value = goals
    }
}
