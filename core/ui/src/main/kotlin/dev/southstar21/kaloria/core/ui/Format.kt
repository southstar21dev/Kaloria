package dev.southstar21.kaloria.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.southstar21.kaloria.core.designsystem.component.grouped
import dev.southstar21.kaloria.core.designsystem.component.roundToDisplay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun Double?.kcalText(): String = this?.roundToLong()?.let { it.grouped() } ?: "–"

fun Double?.gramsText(unit: String = "g"): String = this?.let { "${it.roundToDisplay()} $unit" } ?: "–"

fun Instant.timeText(zone: ZoneId = ZoneId.systemDefault()): String = atZone(zone).toLocalTime().format(timeFormatter)

fun java.time.LocalTime.timeText(): String = format(timeFormatter)

/** "Today", "Yesterday", "Tomorrow", else the short date, in the app's current language. */
@Composable
fun LocalDate.dayTitle(today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMM", locale) }
    return when (this) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        today.plusDays(1) -> stringResource(R.string.day_tomorrow)
        else -> format(formatter)
    }
}

/** Parses user input with either decimal separator ("12,5" on Hungarian keyboards, "12.5"). */
fun String.parseDecimal(): Double? = trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** The inverse of [parseDecimal] for prefilling fields: no trailing ".0". */
fun Double.toInputText(): String = if (this == Math.floor(this) && !isInfinite()) toLong().toString() else "%.2f".format(java.util.Locale.ROOT, this).trimEnd('0')
