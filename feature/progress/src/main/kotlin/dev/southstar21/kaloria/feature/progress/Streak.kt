package dev.southstar21.kaloria.feature.progress

import java.time.LocalDate

/**
 * Days in a row with something logged. Today doesn't break the streak until it's over: until
 * something is logged today, the streak is the run that ended yesterday ([loggedToday] is false).
 */
data class Streak(val current: Int, val best: Int, val totalDays: Int, val loggedToday: Boolean) {
    companion object {
        fun of(days: Set<LocalDate>, today: LocalDate): Streak {
            val loggedToday = today in days
            var day = if (loggedToday) today else today.minusDays(1)
            var current = 0
            while (day in days) {
                current++
                day = day.minusDays(1)
            }
            val past = days.filter { !it.isAfter(today) }.sorted()
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for (d in past) {
                run = if (previous?.plusDays(1) == d) run + 1 else 1
                best = maxOf(best, run)
                previous = d
            }
            return Streak(current, best, past.size, loggedToday)
        }
    }
}
