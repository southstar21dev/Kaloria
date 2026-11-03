package dev.southstar21.kaloria.core.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.southstar21.kaloria.core.designsystem.component.LocalReduceTransparency

/** Kaloria's silver-frost theme, the only one: it ignores the system light/dark setting and never uses wallpaper colors. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KaloriaTheme(
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = KaloriaColors,
        motionScheme = MotionScheme.expressive(),
        typography = KaloriaTypography,
    ) {
        CompositionLocalProvider(LocalReduceTransparency provides reduceTransparency, content = content)
    }
}
