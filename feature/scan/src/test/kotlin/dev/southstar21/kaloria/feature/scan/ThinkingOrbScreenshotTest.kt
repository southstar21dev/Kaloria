package dev.southstar21.kaloria.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.OrbState
import dev.southstar21.kaloria.core.designsystem.component.ThinkingOrb
import dev.southstar21.kaloria.core.testing.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The orbs as the camera shows them: light dots on black, at the start-up and status-card sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h500dp-xxhdpi")
class ThinkingOrbScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun orbs() {
        compose.screenshot("thinking_orbs") {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        ThinkingOrb(OrbState.Searching, size = 120.dp, dark = true, animate = false)
                        ThinkingOrb(OrbState.Working, size = 120.dp, dark = true, animate = false)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        ThinkingOrb(OrbState.Searching, size = 40.dp, dark = true, animate = false)
                        ThinkingOrb(OrbState.Working, size = 40.dp, dark = true, animate = false)
                    }
                }
            }
        }
    }
}
