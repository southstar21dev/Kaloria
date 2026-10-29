package dev.southstar21.kaloria.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Kaloria ink: a deep navy (#1F4E79) on warm cream, with coral (#E07A5F) for highlights and
// "over target". The app always uses these, never the wallpaper's colors.

/** Brand colors used outside the Material roles: the glass background glow and the icon. */
object Brand {
    val Ink = Color(0xFF1F4E79)
    val Coral = Color(0xFFE07A5F)
    val Mist = Color(0xFFB4CDE4)
    val DeepInk = Color(0xFF153E63)
    val Ember = Color(0xFF8A3A2A)
}

val KaloriaLightColors = lightColorScheme(
    primary = Color(0xFF1F4E79),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4F7),
    onPrimaryContainer = Color(0xFF001D33),
    inversePrimary = Color(0xFFA9C8EA),
    secondary = Color(0xFF56606B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE3EE),
    onSecondaryContainer = Color(0xFF131C25),
    tertiary = Color(0xFF9E4330),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD2),
    onTertiaryContainer = Color(0xFF3B0A02),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F5EE),
    onBackground = Color(0xFF1B1F24),
    surface = Color(0xFFF8F5EE),
    onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFE3E2DC),
    onSurfaceVariant = Color(0xFF45474B),
    surfaceTint = Color(0xFF1F4E79),
    inverseSurface = Color(0xFF2F3236),
    inverseOnSurface = Color(0xFFF1EEE7),
    outline = Color(0xFF75777B),
    outlineVariant = Color(0xFFC6C6C2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F5EE),
    surfaceDim = Color(0xFFDAD7D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F0E9),
    surfaceContainer = Color(0xFFEEEBE4),
    surfaceContainerHigh = Color(0xFFE8E5DE),
    surfaceContainerHighest = Color(0xFFE2DFD8),
)

val KaloriaDarkColors = darkColorScheme(
    primary = Color(0xFFA9C8EA),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF153E63),
    onPrimaryContainer = Color(0xFFD2E4F7),
    inversePrimary = Color(0xFF1F4E79),
    secondary = Color(0xFFBEC7D3),
    onSecondary = Color(0xFF28313B),
    secondaryContainer = Color(0xFF3F4852),
    onSecondaryContainer = Color(0xFFDAE3EE),
    tertiary = Color(0xFFFFB4A3),
    onTertiary = Color(0xFF5F1608),
    tertiaryContainer = Color(0xFF7F2C1D),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1620),
    onBackground = Color(0xFFDDE3EA),
    surface = Color(0xFF0F1620),
    onSurface = Color(0xFFDDE3EA),
    surfaceVariant = Color(0xFF3A424C),
    onSurfaceVariant = Color(0xFFC0C7D0),
    surfaceTint = Color(0xFFA9C8EA),
    inverseSurface = Color(0xFFDDE3EA),
    inverseOnSurface = Color(0xFF2B3139),
    outline = Color(0xFF8A929B),
    outlineVariant = Color(0xFF3A424C),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343C47),
    surfaceDim = Color(0xFF0F1620),
    surfaceContainerLowest = Color(0xFF0A1018),
    surfaceContainerLow = Color(0xFF161E28),
    surfaceContainer = Color(0xFF1A222C),
    surfaceContainerHigh = Color(0xFF242C37),
    surfaceContainerHighest = Color(0xFF2F3742),
)

/**
 * Fixed colors for data, the same in both themes and every chart. Chosen to sit alongside the
 * ink brand without one being mistaken for another.
 */
object MacroColors {
    val Protein = Color(0xFF5B8DEF)
    val Carbs = Color(0xFFF2B33D)
    val Fat = Color(0xFFE5677D)
    val Fiber = Color(0xFFB57EDC)
}

object TrackerColors {
    val Water = Color(0xFF3BAFE6)
    val Steps = Color(0xFFE8794A)
}
