package dev.southstar21.kaloria.core.model

/** Daily targets, all entered by hand. A `null` target is simply not shown. */
data class Goals(
    val energyKcal: Int? = null,
    val protein: Int? = null,
    val carbohydrates: Int? = null,
    val fat: Int? = null,
    val fiber: Int? = null,
    val waterMl: Int? = null,
    val steps: Int? = null,
    val micros: Map<Micronutrient, Double> = emptyMap(),
)
