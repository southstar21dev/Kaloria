package dev.southstar21.kaloria.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import dev.southstar21.kaloria.core.datastore.Session
import dev.southstar21.kaloria.core.datastore.SyncInfo
import dev.southstar21.kaloria.core.datastore.UserPreferences
import dev.southstar21.kaloria.core.sync.AccountState
import dev.southstar21.kaloria.core.testing.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val goals = GoalsForm(energyKcal = "2200", protein = "150", carbohydrates = "230", fat = "75", waterMl = "2500", steps = "9000")

    private fun render(name: String, dark: Boolean, account: AccountState, form: SignInForm, edited: Boolean = false) =
        compose.screenshot(name, dark, dock = true) {
            SettingsScreen(
                goals = GoalsEditorState(goals, if (edited) goals.copy(protein = "140") else goals),
                preferences = UserPreferences(),
                account = { AccountCard(account, false, form, {}, {}, {}, {}) },
                onEditGoals = {}, onSaveGoals = {}, onReduceTransparency = {},
            )
        }

    private val signedOut = AccountState(null, "https://apikaloria.southstar21.dev", SyncInfo())

    @Test fun signed_out_light() = render("settings_signed_out", false, signedOut, SignInForm("https://apikaloria.southstar21.dev"))
    @Test fun signed_out_dark() = render("settings_signed_out", true, signedOut, SignInForm("https://apikaloria.southstar21.dev"))
    @Test fun sign_in_error() = render(
        "settings_sign_in_error", false, signedOut,
        SignInForm("http://10.0.2.2:8090", "me@kaloria.local", "x", error = "Wrong email or password."),
    )
    @Test fun signed_in_edited() = render(
        "settings_signed_in", false,
        AccountState(Session("https://apikaloria.southstar21.dev", "u", "me@southstar21.dev", "t"), "https://apikaloria.southstar21.dev",
            SyncInfo(lastSyncAt = System.currentTimeMillis() - 4 * 60_000)),
        SignInForm(), edited = true,
    )
}
