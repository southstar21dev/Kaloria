package dev.southstar21.kaloria.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.data.repository.SettingsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.atwaterKcal
import dev.southstar21.kaloria.core.sync.AccountRepository
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    fun toGoals(previous: Goals) = previous.copy(
        energyKcal = energyKcal.toIntOrNull(),
        protein = protein.toIntOrNull(),
        carbohydrates = carbohydrates.toIntOrNull(),
        fat = fat.toIntOrNull(),
        fiber = fiber.toIntOrNull(),
        steps = steps.toIntOrNull(),
    )

    /** What the macro targets add up to, to compare with the energy target. Fiber isn't counted: it's a minimum, not a share. */
    val macroKcal: Int?
        get() {
            val p = protein.toIntOrNull() ?: return null
            val c = carbohydrates.toIntOrNull() ?: return null
            val f = fat.toIntOrNull() ?: return null
            return atwaterKcal(p.toDouble(), c.toDouble(), f.toDouble()).roundToInt()
        }

    companion object {
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

data class GoalsEditorState(val form: GoalsForm = GoalsForm(), val saved: GoalsForm = GoalsForm()) {
    val changed get() = form != saved
}

data class SignInForm(
    val serverUrl: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val goalsRepository: GoalsRepository,
    private val settings: SettingsRepository,
    private val account: AccountRepository,
) : ViewModel() {

    val accountState = account.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncing = account.isSyncing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var signIn by mutableStateOf(SignInForm())
        private set

    /** Snapshot state so the goal text fields never lag behind typing. */
    var goals by mutableStateOf(GoalsEditorState())
        private set

    val preferences = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var current = Goals()

    init {
        viewModelScope.launch {
            signIn = signIn.copy(serverUrl = account.state.first().serverUrl)
        }
        viewModelScope.launch {
            current = goalsRepository.goals.first()
            val form = GoalsForm.from(current)
            goals = GoalsEditorState(form, form)
        }
    }

    fun editGoals(transform: GoalsForm.() -> GoalsForm) {
        goals = goals.copy(form = goals.form.transform())
    }

    fun saveGoals() = viewModelScope.launch {
        val form = goals.form
        current = form.toGoals(current)
        goalsRepository.save(current)
        goals = goals.copy(saved = form)
    }

    fun editSignIn(transform: SignInForm.() -> SignInForm) {
        signIn = signIn.transform().copy(error = null)
    }

    fun signIn() {
        val form = signIn
        if (form.busy) return
        signIn = form.copy(busy = true, error = null)
        viewModelScope.launch {
            signIn = try {
                account.signIn(form.serverUrl, form.email, form.password)
                form.copy(password = "", busy = false)
            } catch (e: Exception) {
                form.copy(busy = false, error = e.message ?: "Sign-in failed")
            }
        }
    }

    fun signOut() = viewModelScope.launch { account.signOut() }

    fun syncNow() = account.syncNow()

    fun setReduceTransparency(enabled: Boolean) = viewModelScope.launch { settings.setReduceTransparency(enabled) }
}
