package dev.southstar21.kaloria.feature.foods

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The user's own food database. */
@Serializable
data object FoodsKey : NavKey

/**
 * Create ([foodId] null) or edit a food. [name] and [barcode] prefill a new food, e.g. from a
 * search with no results or an unknown barcode; [draftId] prefills it from a scanned label.
 */
@Serializable
data class FoodEditorKey(
    val foodId: String? = null,
    val name: String? = null,
    val barcode: String? = null,
    val draftId: String? = null,
) : NavKey
