package dev.southstar21.kaloria.core.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.southstar21.kaloria.core.designsystem.component.GlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaTheme

/**
 * Renders [content] the way the app does (brand theme, glass backdrop, dock padding) and saves
 * it to src/test/screenshots/<name>.png. Record with `recordRoborazziDebug`.
 *
 * @param screen capture the whole window instead of the root node, for sheets and dialogs.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.screenshot(
    name: String,
    dock: Boolean = false,
    screen: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent {
        KaloriaTheme {
            GlassBackdrop {
                CompositionLocalProvider(LocalDockPadding provides if (dock) 116.dp else 0.dp) { content() }
            }
        }
    }
    waitForIdle()
    val path = "src/test/screenshots/$name.png"
    if (screen) captureScreenRoboImage(path) else onRoot().captureRoboImage(path)
}
