package dev.southstar21.kaloria.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import dev.southstar21.kaloria.core.testing.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DockScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun render(name: String, dark: Boolean, open: Boolean, qualifiers: String = "") {
        if (qualifiers.isNotEmpty()) org.robolectric.RuntimeEnvironment.setQualifiers(qualifiers)
        compose.screenshot(name, dark) {
            Box(Modifier.fillMaxSize()) {
                GlassDock(TopLevelDestination.Progress, open, {}, {}, {})
            }
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi") fun closed_light() = render("dock", false, open = false)
    @Test @Config(qualifiers = "w411dp-h891dp-xxhdpi") fun open_dark() = render("dock_open", true, open = true)
    @Test @Config(qualifiers = "w360dp-h740dp-xhdpi") fun narrow_phone() = render("dock_narrow", false, open = false)
}
