package dev.southstar21.kaloria.feature.foodsearch

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Search foods and log them onto the day [epochDay], or with null onto today as it is when the
 * food is logged, so a search left open past midnight logs onto the new day.
 */
@Serializable
data class FoodSearchKey(val epochDay: Long? = null) : NavKey
