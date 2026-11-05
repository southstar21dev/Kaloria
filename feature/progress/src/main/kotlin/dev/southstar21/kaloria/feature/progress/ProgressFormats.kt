package dev.southstar21.kaloria.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date formats for the progress screen, in the given locale. */
@Immutable
internal class ProgressFormats(locale: Locale) {
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val narrowWeekday: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE", locale)
}

/** Built again when the app's locale changes, not once for the process. */
@Composable
internal fun rememberProgressFormats(): ProgressFormats {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { ProgressFormats(locale) }
}

internal fun axisLabels(days: List<LocalDate>, formats: ProgressFormats): List<String> {
    if (days.size <= 7) return days.map { it.format(formats.narrowWeekday) }
    val step = (days.size - 1) / 4
    return days.indices.map { i -> if ((days.lastIndex - i) % step == 0) days[i].format(formats.shortDate) else "" }
}
