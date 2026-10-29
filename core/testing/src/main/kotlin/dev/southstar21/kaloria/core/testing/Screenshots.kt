package dev.southstar21.kaloria.core.testing

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.southstar21.kaloria.core.designsystem.component.GlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/**
 * Renders [content] the way the app does (brand theme, glass backdrop, dock padding) and saves
 * it to src/test/screenshots/<name>_<light|dark>.png. Record with `recordRoborazziDebug`.
 *
 * @param screen capture the whole window instead of the root node, for sheets and dialogs.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.screenshot(
    name: String,
    dark: Boolean,
    dock: Boolean = false,
    screen: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent {
        KaloriaTheme(darkTheme = dark) {
            GlassBackdrop {
                CompositionLocalProvider(LocalDockPadding provides if (dock) 116.dp else 0.dp) { content() }
            }
        }
    }
    waitForIdle()
    val path = "src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png"
    if (screen) captureScreenRoboImage(path) else onRoot().captureRoboImage(path)
}
