package dev.southstar21.kaloria.core.model

import java.time.Instant

/**
 * One item on the day's timeline. The food's name and per-100 values are copied in when it's
 * logged, so later edits to (or deletion of) the food don't rewrite history.
 */
data class LogEntry(
    val id: String,
    val foodId: String?,
    val foodName: String,
    val brand: String?,
    val amount: Double,
    val unit: AmountUnit,
    val nutrientsPer100: Nutrients,
    val loggedAt: Instant,
    val note: String? = null,
) {
    val nutrients: Nutrients get() = nutrientsPer100.scaledTo(amount)
}

/** A weigh-in. [fromHealth] marks one imported from another app through Health Connect. */
data class WeightEntry(val id: String, val kilograms: Double, val loggedAt: Instant, val fromHealth: Boolean = false)
