package dev.southstar21.kaloria.core.database.model

import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients

data class NutrientColumns(
    val energyKcal: Double?,
    val protein: Double?,
    val carbohydrates: Double?,
    val sugars: Double?,
    val fat: Double?,
    val saturatedFat: Double?,
    val fiber: Double?,
    val salt: Double?,
    /** Micronutrient key -> amount, see [Micronutrient.key]. */
    val micros: Map<String, Double>,
)

fun NutrientColumns.asExternalModel() = Nutrients(
    energyKcal = energyKcal,
    protein = protein,
    carbohydrates = carbohydrates,
    sugars = sugars,
    fat = fat,
    saturatedFat = saturatedFat,
    fiber = fiber,
    salt = salt,
    micros = micros.mapNotNull { (key, value) -> Micronutrient.fromKey(key)?.let { it to value } }.toMap(),
)

fun Nutrients.asColumns() = NutrientColumns(
    energyKcal = energyKcal,
    protein = protein,
    carbohydrates = carbohydrates,
    sugars = sugars,
    fat = fat,
    saturatedFat = saturatedFat,
    fiber = fiber,
    salt = salt,
    micros = micros.mapKeys { it.key.key },
)
