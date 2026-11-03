package dev.southstar21.kaloria.feature.progress

import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.sum
import java.time.LocalDate
import java.time.ZoneId

/** Totals per day across a range, and averages over the days that have any entries. */
data class ProgressSummary(
    val days: List<LocalDate>,
    /** Per day, null when nothing was logged (a gap, not zero). */
    val energy: List<Double?>,
    val loggedDays: Int,
    val average: Nutrients,
) {
    companion object {
        fun of(entries: List<LogEntry>, from: LocalDate, toInclusive: LocalDate, zone: ZoneId): ProgressSummary {
            val byDay = entries.groupBy { it.loggedAt.atZone(zone).toLocalDate() }.mapValues { (_, e) -> e.map { it.nutrients }.sum() }
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.toList()
            val logged = byDay.values
            val n = logged.size
            fun avg(f: (Nutrients) -> Double?) = if (n == 0) null else logged.mapNotNull(f).takeIf { it.isNotEmpty() }?.sum()?.div(n)
            return ProgressSummary(
                days = days,
                energy = days.map { byDay[it]?.energyKcal },
                loggedDays = n,
                average = Nutrients(
                    energyKcal = avg { it.energyKcal },
                    protein = avg { it.protein },
                    carbohydrates = avg { it.carbohydrates },
                    sugars = avg { it.sugars },
                    fat = avg { it.fat },
                    saturatedFat = avg { it.saturatedFat },
                    fiber = avg { it.fiber },
                    salt = avg { it.salt },
                    micros = Micronutrient.entries.mapNotNull { m -> avg { it.micros[m] }?.let { m to it } }.toMap(),
                ),
            )
        }
    }
}
