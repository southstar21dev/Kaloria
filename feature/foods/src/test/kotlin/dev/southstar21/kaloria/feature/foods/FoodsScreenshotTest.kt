package dev.southstar21.kaloria.feature.foods

import androidx.compose.ui.test.junit4.createComposeRule
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.scanner.label.ExtractionMethod
import dev.southstar21.kaloria.core.testing.screenshot
import dev.southstar21.kaloria.feature.foods.editor.FoodEditorScreen
import dev.southstar21.kaloria.feature.foods.editor.FoodEditorState
import dev.southstar21.kaloria.feature.foods.editor.FoodForm
import dev.southstar21.kaloria.feature.foods.editor.ScanReview
import dev.southstar21.kaloria.feature.foods.list.FoodsScreen
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val trappista = Food(
        "a", "Trappista sajt", "Pannontej", "5998200467004", FoodSource.Label, null, AmountUnit.Gram,
        Nutrients(
            energyKcal = 357.0,
            protein = 26.0,
            carbohydrates = 0.0,
            sugars = 0.0,
            fat = 28.0,
            saturatedFat = 18.0,
            salt = 1.7,
            micros = mapOf(Micronutrient.Calcium to 800.0),
        ),
        servings = listOf(Serving("1 slice", 14.0)), favorite = true, updatedAt = Instant.EPOCH,
    )

    private val foods = listOf(
        trappista,
        trappista.copy(
            id = "b",
            name = "Coca-Cola",
            brand = "Coca-Cola",
            source = FoodSource.OpenFoodFacts,
            unit = AmountUnit.Millilitre,
            favorite = false,
            nutrients = Nutrients(energyKcal = 42.0, protein = 0.0, carbohydrates = 10.6, fat = 0.0),
        ),
        trappista.copy(
            id = "c",
            name = "Bananas, raw",
            brand = null,
            source = FoodSource.Usda,
            favorite = false,
            nutrients = Nutrients(energyKcal = 89.0, protein = 1.1, carbohydrates = 22.8, fat = 0.3),
        ),
        trappista.copy(
            id = "d",
            name = "Zabpehely",
            brand = null,
            source = FoodSource.Manual,
            favorite = false,
            nutrients = Nutrients(energyKcal = 372.0, protein = 13.5, carbohydrates = 58.7, fat = 7.0),
        ),
    )

    @Test fun list() = compose.screenshot("foods", dock = true) { FoodsScreen("", {}, foods, {}) }

    @Test fun list_empty() = compose.screenshot("foods_empty", dock = true) { FoodsScreen("xyz", {}, emptyList(), {}) }

    private fun editor(name: String, state: FoodEditorState) = compose.screenshot(name) { FoodEditorScreen(state, {}, {}, {}, {}) }

    @Test fun new_with_errors() = editor(
        "editor_new_errors",
        FoodEditorState(FoodForm(name = "", energy = "", protein = "30", fat = "20", saturatedFat = "25"), loading = false, showErrors = true),
    )

    @Test fun existing() = editor("editor_existing", FoodEditorState(FoodForm.from(trappista), trappista, loading = false))

    @Test fun scan_review() = editor(
        "editor_scan",
        FoodEditorState(
            FoodForm(energy = "820", protein = "24", carbohydrates = "2", fat = "24", salt = "1,8"),
            loading = false,
            scan = ScanReview(
                photo = null,
                method = ExtractionMethod.GeminiNano,
                warnings = mapOf(
                    "energy" to listOf("Energy doesn't match the macros (they imply 320 kcal)."),
                    "protein" to listOf("AI read 24, the text reads 21. Check the photo."),
                ),
            ),
        ),
    )
}
