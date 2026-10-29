package dev.southstar21.kaloria.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.southstar21.kaloria.core.designsystem.component.LocalReduceTransparency

/** Kaloria's own violet palette in light and dark; wallpaper-based dynamic color is never used. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KaloriaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) KaloriaDarkColors else KaloriaLightColors,
        motionScheme = MotionScheme.expressive(),
        typography = KaloriaTypography,
    ) {
        CompositionLocalProvider(LocalReduceTransparency provides reduceTransparency, content = content)
    }
}
