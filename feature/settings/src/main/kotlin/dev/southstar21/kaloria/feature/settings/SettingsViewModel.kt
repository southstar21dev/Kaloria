package dev.southstar21.kaloria.feature.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.data.repository.SettingsRepository
import dev.southstar21.kaloria.core.sync.AccountRepository
import dev.southstar21.kaloria.core.sync.SignInException
import dev.southstar21.kaloria.core.sync.SignInResult
import dev.southstar21.kaloria.core.sync.SwitchChoice
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SignInForm(
    val serverUrl: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val goalsRepository: GoalsRepository,
    private val settings: SettingsRepository,
    private val account: AccountRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val accountState = account.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncing = account.isSyncing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Server and email survive process death; the password is never saved.
    var signIn by mutableStateOf(SignInForm(serverUrl = savedState[SERVER_URL] ?: "", email = savedState[EMAIL] ?: ""))
        private set

    private val goalsEditor = GoalsEditor(goalsRepository, savedState, viewModelScope)

    /** The goals form; snapshot state, so the goal text fields never lag behind typing. */
    val goals: GoalsEditorState get() = goalsEditor.state

    val preferences = settings.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (!savedState.contains(SERVER_URL)) {
            viewModelScope.launch {
                signIn = signIn.copy(serverUrl = account.state.first().serverUrl)
            }
        }
    }

    fun editGoals(transform: GoalsForm.() -> GoalsForm) = goalsEditor.edit(transform)

    fun saveGoals() = goalsEditor.save()

    fun editSignIn(transform: SignInForm.() -> SignInForm) {
        signIn = signIn.copy(errorRes = null).transform()
        savedState[SERVER_URL] = signIn.serverUrl
        savedState[EMAIL] = signIn.email
    }

    fun signIn() {
        val form = signIn
        if (form.busy) return
        signIn = form.copy(busy = true, errorRes = null)
        viewModelScope.launch {
            // The result applies to the form as it is now, not to the copy that was sent, so
            // anything typed while signing in is kept.
            signIn = try {
                when (account.signIn(form.serverUrl, form.email, form.password)) {
                    SignInResult.SignedIn -> signIn.copy(password = "", busy = false)

                    // The account card asks what to do with this phone's data first.
                    SignInResult.NeedsChoice -> signIn.copy(busy = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                signIn.copy(busy = false, errorRes = signInMessage(e))
            }
        }
    }

    /** Finishes a sign-in that was waiting for the user to say what happens to this phone's data. */
    fun resolveSwitch(choice: SwitchChoice) {
        viewModelScope.launch {
            signIn = try {
                account.resolveSwitch(choice)
                signIn.copy(password = "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                account.cancelSwitch()
                signIn.copy(errorRes = signInMessage(e))
            }
        }
    }

    fun cancelSwitch() = account.cancelSwitch()

    /** What went wrong in words for the account card; anything unexpected is a plain "sign-in failed". */
    @StringRes
    private fun signInMessage(e: Exception): Int = (e as? SignInException)?.reason?.messageRes ?: R.string.settings_account_sign_in_failed

    fun signOut() = viewModelScope.launch { account.signOut() }

    fun syncNow() = account.syncNow()

    fun setReduceTransparency(enabled: Boolean) = viewModelScope.launch { settings.setReduceTransparency(enabled) }

    private companion object {
        const val SERVER_URL = "sign_in_server_url"
        const val EMAIL = "sign_in_email"
    }
}
