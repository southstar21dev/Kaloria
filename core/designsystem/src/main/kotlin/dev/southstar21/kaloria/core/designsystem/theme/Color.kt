package dev.southstar21.kaloria.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Silver frost: ink (#1F4E79) for everything active and coral for "over target", on milky glass
// over a silvery periwinkle field, with faint pastel glints. The app always uses these, never the
// wallpaper's colors.

/** Colors used outside the Material roles: the silver field, its fabric folds and pastel glints. */
object Brand {
    val Ink = Color(0xFF1F4E79)
    val SilverTop = Color(0xFFDCE2EE)
    val SilverMid = Color(0xFFB9C6DD)
    val SilverLow = Color(0xFF9FB0D0)
    val FoldShade = Color(0xFF7E93BB)
    val FoldShadeSoft = Color(0xFF8CA0C6)
    val FoldLight = Color(0xFFF2F5FA)
    val GlintPink = Color(0xFFFFC8E6)
    val GlintBlue = Color(0xFFBEDCFF)
    val GlintGold = Color(0xFFFFF0C8)
}

/**
 * The one theme: silver frost. Milky glass and white surfaces over a silvery periwinkle field,
 * ink text and controls, coral for "over target". Kaloria ignores the system light/dark setting.
 */
val KaloriaColors = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4F7),
    onPrimaryContainer = Color(0xFF0D2A47),
    inversePrimary = Color(0xFFA9C8EA),
    secondary = Color(0xFF4A5B78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4F6FA),
    onSecondaryContainer = Color(0xFF1D2C44),
    tertiary = Color(0xFFB4462F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3B0A02),
    error = Color(0xFFB4462F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD2),
    onErrorContainer = Color(0xFF3B0A02),
    background = Color(0xFFC9D2E3),
    onBackground = Color(0xFF1D2C44),
    surface = Color(0xFFF4F6FA),
    onSurface = Color(0xFF1D2C44),
    surfaceVariant = Color(0xFFDDE3EE),
    onSurfaceVariant = Color(0xFF4A5B78),
    surfaceTint = Color(0xFF1F4E79),
    inverseSurface = Color(0xFF1D2C44),
    inverseOnSurface = Color(0xFFF4F6FA),
    outline = Color(0xFF7D8BA3),
    outlineVariant = Color(0xFFC3CCDB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FC),
    surfaceDim = Color(0xFFC9D2E3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6FA),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE6EAF2),
    surfaceContainerHighest = Color(0xFFDEE3EC),
)

/** Deep ink, only for controls over the live camera, where the glass is dark tinted. */
val KaloriaCameraColors = darkColorScheme(
    primary = Color(0xFFA9C8EA),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF153E63),
    onPrimaryContainer = Color(0xFFD2E4F7),
    inversePrimary = Color(0xFF1F4E79),
    secondary = Color(0xFFB4C8DC),
    onSecondary = Color(0xFF13283D),
    secondaryContainer = Color(0xFF18304A),
    onSecondaryContainer = Color(0xFFD2E4F7),
    tertiary = Color(0xFFFFB4A3),
    onTertiary = Color(0xFF5F1608),
    tertiaryContainer = Color(0xFF7F2C1D),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Color(0xFFFFB4A3),
    onError = Color(0xFF5F1608),
    errorContainer = Color(0xFF7F2C1D),
    onErrorContainer = Color(0xFFFFDAD2),
    background = Color(0xFF10263D),
    onBackground = Color(0xFFDDE3EA),
    surface = Color(0xFF10263D),
    onSurface = Color(0xFFDDE3EA),
    surfaceVariant = Color(0xFF1F3550),
    onSurfaceVariant = Color(0xFF8FA4BA),
    surfaceTint = Color(0xFFA9C8EA),
    inverseSurface = Color(0xFFDDE3EA),
    inverseOnSurface = Color(0xFF13283D),
    outline = Color(0xFF5E7790),
    outlineVariant = Color(0xFF1F3550),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1E3A57),
    surfaceDim = Color(0xFF10263D),
    surfaceContainerLowest = Color(0xFF07131F),
    surfaceContainerLow = Color(0xFF0E2236),
    surfaceContainer = Color(0xFF0E2236),
    surfaceContainerHigh = Color(0xFF132A42),
    surfaceContainerHighest = Color(0xFF1A3450),
)
