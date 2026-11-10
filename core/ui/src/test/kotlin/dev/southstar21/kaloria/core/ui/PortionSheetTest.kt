package dev.southstar21.kaloria.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.southstar21.kaloria.core.designsystem.component.GlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.LocalBakeFieldInBackground
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaTheme
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PortionSheetTest {
    @get:Rule val compose = createComposeRule()

    private var servings by mutableStateOf(emptyList<Serving>())
    private var saved: List<Serving>? = null

    private fun open() = compose.setContent {
        KaloriaTheme {
            CompositionLocalProvider(LocalBakeFieldInBackground provides false) {
                GlassBackdrop {
                    PortionSheet(
                        title = "Bread", subtitle = null, unit = AmountUnit.Gram, per100 = Nutrients(energyKcal = 250.0),
                        servings = servings, initialAmount = 45.0, initialTime = LocalTime.NOON, confirmLabel = "Log",
                        onConfirm = { _, _ -> }, onDismiss = {}, onServingsChange = { saved = it },
                    )
                }
            }
        }
    }

    @Test
    fun `servings read after the sheet opened are shown`() {
        open()
        servings = listOf(Serving("slice", 28.0), Serving("pack", 200.0))

        compose.onNodeWithText("slice · 28 g").assertExists()
        compose.onNodeWithText("pack · 200 g").assertExists()
    }

    @Test
    fun `adding a serving keeps the food's others`() {
        open()
        servings = listOf(Serving("slice", 28.0), Serving("pack", 200.0))
        compose.waitForIdle()

        compose.onNodeWithText("Serving").performClick()
        compose.onNodeWithText("Name").performTextInput("bowl")
        compose.onNodeWithText("Save").performClick()

        assertEquals(listOf(Serving("slice", 28.0), Serving("pack", 200.0), Serving("bowl", 45.0)), saved)
    }
}
