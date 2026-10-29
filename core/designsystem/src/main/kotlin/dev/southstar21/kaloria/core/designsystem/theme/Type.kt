package dev.southstar21.kaloria.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle

private val Base = Typography()

/** Tighter, heavier display styles for the big numbers (remaining kcal, ring centers). */
internal val KaloriaTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Black),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
)
