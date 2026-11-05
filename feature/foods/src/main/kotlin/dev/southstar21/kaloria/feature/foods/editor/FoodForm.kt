package dev.southstar21.kaloria.feature.foods.editor

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.scanner.label.LabelExtraction
import dev.southstar21.kaloria.core.ui.parseDecimal
import dev.southstar21.kaloria.core.ui.toInputText
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlinx.serialization.Serializable

enum class EnergyUnit { Kcal, Kj }

@Serializable
data class ServingInput(val name: String = "", val amount: String = "")

/** Raw text of every field in the food editor, exactly as typed. */
@Serializable
data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val barcode: String = "",
    val unit: AmountUnit = AmountUnit.Gram,
    val energy: String = "",
    val energyUnit: EnergyUnit = EnergyUnit.Kcal,
    val protein: String = "",
    val carbohydrates: String = "",
    val sugars: String = "",
    val fat: String = "",
    val saturatedFat: String = "",
    val fiber: String = "",
    val salt: String = "",
    val micros: Map<Micronutrient, String> = emptyMap(),
    val servings: List<ServingInput> = emptyList(),
    val favorite: Boolean = false,
) {
    val energyKcal: Double?
        get() = energy.parseDecimal()?.let { if (energyUnit == EnergyUnit.Kj) it / Nutrients.KJ_PER_KCAL else it }

    fun nutrients() = Nutrients(
        energyKcal = energyKcal,
        protein = protein.parseDecimal(),
        carbohydrates = carbohydrates.parseDecimal(),
        sugars = sugars.parseDecimal(),
        fat = fat.parseDecimal(),
        saturatedFat = saturatedFat.parseDecimal(),
        fiber = fiber.parseDecimal(),
        salt = salt.parseDecimal(),
        micros = micros.mapNotNull { (m, text) -> text.parseDecimal()?.let { m to it } }.toMap(),
    )

    val errors: FormErrors
        get() {
            val n = nutrients()
            fun bad(text: String) = text.isNotBlank() && text.parseDecimal() == null
            return FormErrors(
                name = name.isBlank(),
                energy = n.energyKcal == null,
                unparseable = listOf(protein, carbohydrates, sugars, fat, saturatedFat, fiber, salt).any(::bad) ||
                    micros.values.any(::bad) || servings.any { bad(it.amount) },
                // Parts can't exceed the whole: sugars ≤ carbs, saturated ≤ fat, and macros ≤ 100 g per 100 g.
                sugarsOverCarbs = n.sugars != null && n.carbohydrates != null && n.sugars!! > n.carbohydrates!! + 0.05,
                saturatedOverFat = n.saturatedFat != null && n.fat != null && n.saturatedFat!! > n.fat!! + 0.05,
                macrosOver100 = unit == AmountUnit.Gram &&
                    listOfNotNull(n.protein, n.carbohydrates, n.fat, n.fiber, n.salt).sum() > 100.5,
                // Pure fat is ~900 kcal/100 g, and no single nutrient can exceed 100 g per 100.
                implausible = (n.energyKcal ?: 0.0) > MAX_KCAL_PER_100 ||
                    listOfNotNull(n.protein, n.carbohydrates, n.sugars, n.fat, n.saturatedFat, n.fiber, n.salt)
                        .any { it > 100.0 },
            )
        }

    /**
     * Mismatch between stated energy and what the macros imply, when it's larger than rounding on
     * a label explains. Shown as a warning only: polyols, alcohol and organic acids are legitimate
     * reasons for a gap.
     */
    val energyMismatchKcal: Double?
        get() {
            val n = nutrients()
            val stated = n.energyKcal ?: return null
            val implied = n.atwaterKcal ?: return null
            val diff = implied - stated
            return diff.takeIf { abs(it) > max(20.0, stated * 0.15) }
        }

    fun toFood(id: String, source: FoodSource, sourceRef: String?): Food = Food(
        id = id,
        name = name.trim(),
        brand = brand.trim().ifEmpty { null },
        barcode = barcode.trim().ifEmpty { null },
        source = source,
        sourceRef = sourceRef,
        unit = unit,
        nutrients = nutrients(),
        servings = servings.mapNotNull { s ->
            val amount = s.amount.parseDecimal() ?: return@mapNotNull null
            Serving(s.name.trim().ifEmpty { "1 serving" }, amount)
        },
        favorite = favorite,
        updatedAt = Instant.EPOCH,
    )

    companion object {
        /** A scanned label's values, for the user to check against the photo. */
        fun from(extraction: LabelExtraction, barcode: String?) = FoodForm(
            name = extraction.name.orEmpty(),
            brand = extraction.brand.orEmpty(),
            barcode = barcode.orEmpty(),
            unit = extraction.unit,
            energy = extraction.nutrients.energyKcal?.let { Math.round(it * 10) / 10.0 }?.toInputText().orEmpty(),
            protein = extraction.nutrients.protein?.toInputText().orEmpty(),
            carbohydrates = extraction.nutrients.carbohydrates?.toInputText().orEmpty(),
            sugars = extraction.nutrients.sugars?.toInputText().orEmpty(),
            fat = extraction.nutrients.fat?.toInputText().orEmpty(),
            saturatedFat = extraction.nutrients.saturatedFat?.toInputText().orEmpty(),
            fiber = extraction.nutrients.fiber?.toInputText().orEmpty(),
            salt = extraction.nutrients.salt?.toInputText().orEmpty(),
            micros = extraction.nutrients.micros.mapValues { it.value.toInputText() },
        )

        fun from(food: Food) = FoodForm(
            name = food.name,
            brand = food.brand.orEmpty(),
            barcode = food.barcode.orEmpty(),
            unit = food.unit,
            energy = food.nutrients.energyKcal?.toInputText().orEmpty(),
            protein = food.nutrients.protein?.toInputText().orEmpty(),
            carbohydrates = food.nutrients.carbohydrates?.toInputText().orEmpty(),
            sugars = food.nutrients.sugars?.toInputText().orEmpty(),
            fat = food.nutrients.fat?.toInputText().orEmpty(),
            saturatedFat = food.nutrients.saturatedFat?.toInputText().orEmpty(),
            fiber = food.nutrients.fiber?.toInputText().orEmpty(),
            salt = food.nutrients.salt?.toInputText().orEmpty(),
            micros = food.nutrients.micros.mapValues { it.value.toInputText() },
            servings = food.servings.map { ServingInput(it.name, it.amount.toInputText()) },
            favorite = food.favorite,
        )
    }
}

data class FormErrors(
    val name: Boolean,
    val energy: Boolean,
    val unparseable: Boolean,
    val sugarsOverCarbs: Boolean,
    val saturatedOverFat: Boolean,
    val macrosOver100: Boolean,
    val implausible: Boolean,
) {
    val any get() = name || energy || unparseable || sugarsOverCarbs || saturatedOverFat || macrosOver100 || implausible
}

/** A little above pure fat (≈ 884–900 kcal) to allow for label rounding. */
const val MAX_KCAL_PER_100 = 920.0
