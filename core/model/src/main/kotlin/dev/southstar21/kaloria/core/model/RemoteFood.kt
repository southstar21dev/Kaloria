package dev.southstar21.kaloria.core.model

/**
 * A food found in Open Food Facts or USDA, not yet in the user's database. It's copied in (as a
 * [Food] with a new id) the first time it's logged, keyed by [source] + [sourceRef].
 */
data class RemoteFood(
    val source: FoodSource,
    /** OFF barcode or USDA fdcId. */
    val sourceRef: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val unit: AmountUnit,
    val nutrients: Nutrients,
    val servings: List<Serving> = emptyList(),
    /** e.g. "USDA Foundation", shown next to the name. */
    val detail: String? = null,
)
