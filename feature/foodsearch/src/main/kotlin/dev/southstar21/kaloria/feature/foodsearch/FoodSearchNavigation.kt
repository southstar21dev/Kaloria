package dev.southstar21.kaloria.feature.foodsearch

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Search foods and log them onto the day [epochDay]. */
@Serializable
data class FoodSearchKey(val epochDay: Long) : NavKey
