package dev.southstar21.kaloria.core.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.southstar21.kaloria.core.designsystem.component.grouped
import dev.southstar21.kaloria.core.designsystem.component.roundToDisplay
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

fun Double?.kcalText(): String = this?.roundToLong()?.let { it.grouped() } ?: "–"

fun Double?.gramsText(unit: String): String = this?.let { "${it.roundToDisplay()} $unit" } ?: "–"

/** The grams symbol, from resources so it can be translated. */
@Composable
fun gramsUnit(): String = stringResource(R.string.unit_grams)

@Composable
fun Double?.gramsText(): String = gramsText(gramsUnit())

/**
 * A time of day as the phone shows it: 12- or 24-hour as set in the system settings, in the app's
 * current language ("14:05", "2:05 PM").
 */
@Composable
fun LocalTime.timeText(): String = format(rememberTimeFormatter())

/** The time of day [this] instant falls on, in the app clock's zone. */
@Composable
fun Instant.timeText(): String = atZone(LocalClock.current.zone).toLocalTime().timeText()

@Composable
private fun rememberTimeFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    // Read on every composition rather than remembered: changing the setting doesn't recreate the activity.
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { timeFormatter(locale, is24Hour) }
}

/** The locale's own pattern for hours and minutes, in the 12- or 24-hour clock asked for. */
internal fun timeFormatter(locale: Locale, is24Hour: Boolean): DateTimeFormatter {
    val pattern = runCatching { DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm") }.getOrNull()
    return pattern?.let { DateTimeFormatter.ofPattern(it, locale) } ?: DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
}

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

/**
 * The inverse of [parseDecimal] for prefilling fields: no trailing ".0" or ".", and enough
 * significant digits that small values survive (0.035 mg stays 0.035, not 0.04). Six is more than
 * any label or database states, while a converted value (kJ to kcal) doesn't fill the field.
 */
fun Double.toInputText(): String = when {
    isNaN() || isInfinite() -> ""
    this == Math.floor(this) && kotlin.math.abs(this) < 1e15 -> toLong().toString()
    else -> BigDecimal(this).round(MathContext(INPUT_SIGNIFICANT_DIGITS)).stripTrailingZeros().toPlainString()
}

private const val INPUT_SIGNIFICANT_DIGITS = 6
