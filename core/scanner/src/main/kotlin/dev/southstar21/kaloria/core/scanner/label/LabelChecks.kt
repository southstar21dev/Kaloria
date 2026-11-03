package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.Nutrients
import kotlin.math.abs
import kotlin.math.max

/** Consistency checks on extracted values; each finding highlights a field for review. */
object LabelChecks {
    fun check(n: Nutrients): List<FieldWarning> = buildList {
        val kcal = n.energyKcal
        val implied = n.atwaterKcal
        if (kcal != null && implied != null && abs(implied - kcal) > max(20.0, kcal * 0.15)) {
            add(FieldWarning("energy", "Energy doesn't match the macros (they imply ${implied.toInt()} kcal)."))
        }
        if (kcal != null && kcal > 920) add(FieldWarning("energy", "More than 920 kcal per 100 is impossible."))
        if (n.sugars != null && n.carbohydrates != null && n.sugars!! > n.carbohydrates!! + 0.05) {
            add(FieldWarning("sugars", "Sugars are higher than carbohydrates."))
        }
        if (n.saturatedFat != null && n.fat != null && n.saturatedFat!! > n.fat!! + 0.05) {
            add(FieldWarning("saturatedFat", "Saturated fat is higher than fat."))
        }
        listOf("protein" to n.protein, "carbohydrates" to n.carbohydrates, "fat" to n.fat, "salt" to n.salt)
            .filter { (_, v) -> v != null && v > 100 }
            .forEach { (f, _) -> add(FieldWarning(f, "More than 100 g per 100 is impossible.")) }
        listOf("energy" to kcal, "protein" to n.protein, "carbohydrates" to n.carbohydrates, "fat" to n.fat)
            .filter { it.second == null }
            .forEach { (f, _) -> add(FieldWarning(f, "Not read from the label.")) }
    }
}
