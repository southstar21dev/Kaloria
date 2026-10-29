package dev.southstar21.kaloria.core.model

import java.time.Instant

enum class FoodSource { Label, OpenFoodFacts, Usda, Manual }

/** Whether amounts are weighed in grams or measured in millilitres. */
enum class AmountUnit(val symbol: String) { Gram("g"), Millilitre("ml") }

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
