package dev.southstar21.kaloria.core.model

import java.time.Instant

enum class FoodSource { Label, OpenFoodFacts, Usda, Manual }

/** Whether amounts are weighed in grams or measured in millilitres. */
enum class AmountUnit(val symbol: String) { Gram("g"), Millilitre("ml") }

/**
 * A unit people enter amounts in. Foods are stored per gram or millilitre ([base]); these convert,
 * [perUnit] base units each (US fluid ounce and cup). [decimals] is how finely an amount shows.
 */
enum class PortionUnit(val symbol: String, val base: AmountUnit, val perUnit: Double, val decimals: Int) {
    Gram("g", AmountUnit.Gram, 1.0, 1),
    Kilogram("kg", AmountUnit.Gram, 1000.0, 3),
    Ounce("oz", AmountUnit.Gram, 28.349523125, 2),
    Pound("lb", AmountUnit.Gram, 453.59237, 3),
    Millilitre("ml", AmountUnit.Millilitre, 1.0, 1),
    Litre("l", AmountUnit.Millilitre, 1000.0, 3),
    FluidOunce("fl oz", AmountUnit.Millilitre, 29.5735295625, 2),
    Cup("cup", AmountUnit.Millilitre, 240.0, 2),
    ;

    fun fromBase(amount: Double) = amount / perUnit
    fun toBase(amount: Double) = amount * perUnit

    companion object {
        fun forBase(base: AmountUnit) = entries.filter { it.base == base }
        fun default(base: AmountUnit) = if (base == AmountUnit.Gram) Gram else Millilitre
    }
}

/** A named portion, e.g. "1 slice" = 28 g. */
data class Serving(val name: String, val amount: Double)

/**
 * A food in the user's own database. [nutrients] are per 100 g or 100 ml (see [unit]).
 * Foods from Open Food Facts or USDA are copied here the first time they're logged, so the
 * user's history never depends on an external API.
 */
data class Food(
    val id: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val source: FoodSource,
    val sourceRef: String? = null,
    val unit: AmountUnit = AmountUnit.Gram,
    val nutrients: Nutrients,
    val servings: List<Serving> = emptyList(),
    val favorite: Boolean = false,
    val updatedAt: Instant,
)
