package dev.southstar21.kaloria.feature.progress

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Imported from Health Connect (Samsung Health and others), not logged in Kaloria. */

internal val ShortDate = DateTimeFormatter.ofPattern("d MMM")

internal fun axisLabels(days: List<LocalDate>): List<String> {
    if (days.size <= 7) return days.map { it.format(NarrowWeekday) }
    val step = (days.size - 1) / 4
    return days.indices.map { i -> if ((days.lastIndex - i) % step == 0) days[i].format(ShortDate) else "" }
}

private val NarrowWeekday = DateTimeFormatter.ofPattern("EEEEE")
