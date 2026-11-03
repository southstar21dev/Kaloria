package dev.southstar21.kaloria.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock

/**
 * The app's clock for UI code, provided by MainActivity from the injected one, so screens and
 * ViewModels agree on "today" and tests can pin it.
 */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }
