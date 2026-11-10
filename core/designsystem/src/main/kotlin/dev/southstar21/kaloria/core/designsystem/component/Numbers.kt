package dev.southstar21.kaloria.core.designsystem.component

import java.text.NumberFormat
import java.util.Locale

/**
 * One integer format per thread, rebuilt only when the locale changes: [AnimatedCount] formats on
 * every animation frame, and NumberFormat is neither cheap to create nor safe to share.
 */
private val integerFormat = ThreadLocal<Pair<Locale, NumberFormat>>()

private fun integerFormat(): NumberFormat {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    integerFormat.get()?.let { (cachedLocale, format) -> if (cachedLocale == locale) return format }
    return NumberFormat.getIntegerInstance(locale).also { integerFormat.set(locale to it) }
}

/** Whole numbers with the user's thousands separator (2,200 / 2 200 / 2.200), for display. */
fun Int.grouped(): String = integerFormat().format(this)

fun Long.grouped(): String = integerFormat().format(this)

/** A weight in kilograms to one decimal, in the user's locale: "82.4 kg". */
fun Double.kgText(): String = "%.1f kg".format(this)
