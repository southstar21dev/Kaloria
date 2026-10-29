package dev.southstar21.kaloria.feature.foodsearch

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import dev.southstar21.kaloria.core.data.repository.OnlineResults
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.testing.screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodSearchScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val own = listOf(
        Food("a", "Trappista sajt", "Coop", "5998200467004", FoodSource.OpenFoodFacts, "5998200467004", AmountUnit.Gram,
            Nutrients(energyKcal = 320.0, protein = 24.0, carbohydrates = 2.0, fat = 24.0), favorite = true, updatedAt = Instant.EPOCH),
        Food("b", "Házi túrós lepény anyukám receptje szerint", null, null, FoodSource.Label, null, AmountUnit.Gram,
            Nutrients(energyKcal = 286.0, protein = 9.0, carbohydrates = 38.0, fat = 11.0), updatedAt = Instant.EPOCH),
    )

    private fun remote(i: Int, source: FoodSource) = RemoteFood(
        source, "ref$i", if (source == FoodSource.Usda) "Cheese, trappist, generic $i" else "Trappista $i", if (source == FoodSource.Usda) null else "Brand $i",
        null, AmountUnit.Gram, Nutrients(energyKcal = 330.0 + i, protein = 25.0, carbohydrates = 1.0, fat = 26.0),
        detail = if (source == FoodSource.Usda) "USDA SR Legacy" else null,
    )

    private val online = OnlineState.Loaded(
        OnlineResults(openFoodFacts = (1..8).map { remote(it, FoodSource.OpenFoodFacts) }, usda = (1..3).map { remote(it, FoodSource.Usda) }),
    )

    private fun render(name: String, dark: Boolean, query: String, results: List<Food>, state: OnlineState) =
        compose.screenshot(name, dark) {
            FoodSearchScreen(
                query = query, onQueryChange = {}, results = results, online = state, snackbar = SnackbarHostState(),
                defaultTime = { LocalTime.NOON }, onLog = { _, _, _ -> }, onBack = {}, onCreateFood = {}, onScanBarcode = {},
            )
        }

    @Test fun results_light() = render("search", false, "trappista", own, online)
    @Test fun results_dark() = render("search", true, "trappista", own, online)
    @Test fun empty_light() = render("search_empty", false, "", emptyList(), OnlineState.Idle)
    @Test fun failed() = render(
        "search_failed", false, "zabpehely", emptyList(),
        OnlineState.Loaded(OnlineResults(failed = setOf(FoodSource.OpenFoodFacts, FoodSource.Usda))),
    )
}
