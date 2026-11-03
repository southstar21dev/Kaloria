package dev.southstar21.kaloria.core.model

/**
 * Nutrition values for a fixed reference amount: per 100 g (or 100 ml for liquids) on a [Food],
 * or for the logged amount once [scaledTo] has been applied.
 *
 * Macros are in grams and energy in kcal. A `null` value means the label doesn't state it, which is
 * different from a stated zero and is never counted as zero in totals.
 */
data class Nutrients(
    val energyKcal: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val sugars: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
    val micros: Map<Micronutrient, Double> = emptyMap(),
) {
    /** Values for [amount] grams/ml, given that these values are per 100. */
    fun scaledTo(amount: Double): Nutrients {
        val factor = amount / 100.0
        return Nutrients(
            energyKcal = energyKcal?.times(factor),
            protein = protein?.times(factor),
            carbohydrates = carbohydrates?.times(factor),
            sugars = sugars?.times(factor),
            fat = fat?.times(factor),
            saturatedFat = saturatedFat?.times(factor),
            fiber = fiber?.times(factor),
            salt = salt?.times(factor),
            micros = micros.mapValues { it.value * factor },
        )
    }

    operator fun plus(other: Nutrients): Nutrients = Nutrients(
        energyKcal = energyKcal.plusNullable(other.energyKcal),
        protein = protein.plusNullable(other.protein),
        carbohydrates = carbohydrates.plusNullable(other.carbohydrates),
        sugars = sugars.plusNullable(other.sugars),
        fat = fat.plusNullable(other.fat),
        saturatedFat = saturatedFat.plusNullable(other.saturatedFat),
        fiber = fiber.plusNullable(other.fiber),
        salt = salt.plusNullable(other.salt),
        micros = (micros.keys + other.micros.keys).associateWith {
            (micros[it] ?: 0.0) + (other.micros[it] ?: 0.0)
        },
    )

    /** Sodium in mg, from salt when sodium itself isn't stated (EU labels list salt = sodium × 2.5). */
    val sodiumMg: Double?
        get() = micros[Micronutrient.Sodium] ?: salt?.let { it / 2.5 * 1000 }

    /**
     * Energy implied by the macros using Atwater factors (4/4/9, fiber 2 kcal/g as in EU labelling).
     * Used only to sanity-check scanned labels, never as a displayed or logged value.
     */
    val atwaterKcal: Double?
        get() {
            val p = protein ?: return null
            val c = carbohydrates ?: return null
            val f = fat ?: return null
            return atwaterKcal(p, c, f, fiber ?: 0.0)
        }

    companion object {
        val Empty = Nutrients()
        const val KJ_PER_KCAL = 4.184
    }
}

/** Energy from macros with Atwater factors: 4 kcal/g protein and carbs, 9 fat, 2 fiber (EU labelling). */
fun atwaterKcal(protein: Double, carbohydrates: Double, fat: Double, fiber: Double = 0.0): Double = 4 * protein + 4 * carbohydrates + 9 * fat + 2 * fiber

private fun Double?.plusNullable(other: Double?): Double? = when {
    this == null -> other
    other == null -> this
    else -> this + other
}

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.Empty, Nutrients::plus)
