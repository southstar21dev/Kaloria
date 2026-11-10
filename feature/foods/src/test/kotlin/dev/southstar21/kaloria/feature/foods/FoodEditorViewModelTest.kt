package dev.southstar21.kaloria.feature.foods

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.scanner.label.ScanDraftStore
import dev.southstar21.kaloria.feature.foods.editor.FoodEditorViewModel
import dev.southstar21.kaloria.feature.foods.editor.ServingInput
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodEditorViewModelTest {
    /** As USDA has it: values with more digits than the editor shows. */
    private val milk = Food(
        id = "milk",
        name = "Milk, whole",
        barcode = "111",
        source = FoodSource.Usda,
        sourceRef = "746782",
        nutrients = Nutrients(
            energyKcal = 1000 / 4.184,
            protein = 3.27,
            fat = 3.2512345,
            micros = mapOf(Micronutrient.VitaminB2 to 0.0350123456, Micronutrient.VitaminB1 to 0.0044),
        ),
        servings = listOf(Serving("cup", 244.0123456)),
        updatedAt = Instant.EPOCH,
    )
    private val cheese = Food(id = "cheese", name = "Trappista", barcode = "599", source = FoodSource.Manual, nutrients = Nutrients(energyKcal = 357.0), updatedAt = Instant.EPOCH)

    private val foods = EditorFoods(listOf(milk, cheese))
    private val viewModels = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
    }

    private fun editor(key: FoodEditorKey) = FoodEditorViewModel(key, SavedStateHandle(), foods, ScanDraftStore()).also { viewModels.put(key.toString(), it) }

    @Test
    fun `opening and saving a food keeps its exact values`() {
        val vm = editor(FoodEditorKey(foodId = "milk"))
        assertEquals("0.0350123", vm.state.form.micros[Micronutrient.VitaminB2])

        vm.edit { copy(favorite = true) }
        vm.save()

        val saved = foods.saved.single()
        assertEquals(milk.nutrients, saved.nutrients)
        assertEquals(milk.servings, saved.servings)
        assertTrue(saved.favorite)
    }

    @Test
    fun `an edited field saves what was typed, the others stay exact`() {
        val vm = editor(FoodEditorKey(foodId = "milk"))

        vm.edit { copy(protein = "3.4", micros = micros + (Micronutrient.VitaminB1 to "0.05")) }
        vm.save()

        val saved = foods.saved.single().nutrients
        assertEquals(3.4, saved.protein!!, 0.0)
        assertEquals(0.05, saved.micros[Micronutrient.VitaminB1]!!, 0.0)
        assertEquals(milk.nutrients.energyKcal, saved.energyKcal)
        assertEquals(milk.nutrients.micros[Micronutrient.VitaminB2], saved.micros[Micronutrient.VitaminB2])
    }

    @Test
    fun `a serving given only an amount gets the default name`() {
        val vm = editor(FoodEditorKey(name = "Soup"))

        vm.edit { copy(energy = "45", servings = listOf(ServingInput(amount = "300"))) }
        vm.save(defaultServingName = "1 portion")

        assertEquals(listOf(Serving("1 portion", 300.0)), foods.saved.single().servings)
    }

    @Test
    fun `a serving with a name but no amount, or a zero one, isn't saved silently`() {
        val vm = editor(FoodEditorKey(name = "Soup"))

        vm.edit { copy(energy = "45", servings = listOf(ServingInput(name = "bowl"))) }
        vm.save()
        assertTrue(vm.state.showErrors)
        assertTrue(vm.state.form.errors.servings)

        vm.edit { copy(servings = listOf(ServingInput(name = "bowl", amount = "0"))) }
        vm.save()
        assertTrue(vm.state.form.errors.servings)
        assertTrue(foods.saved.isEmpty())

        // A row left wholly empty is simply dropped.
        vm.edit { copy(servings = listOf(ServingInput())) }
        vm.save()
        assertEquals(emptyList<Serving>(), foods.saved.single().servings)
    }

    @Test
    fun `another food's barcode is pointed out but doesn't stop the save`() {
        val vm = editor(FoodEditorKey(name = "Cheese", barcode = "599"))
        assertEquals("Trappista", vm.state.barcodeUsedBy)

        vm.edit { copy(barcode = "600") }
        assertNull(vm.state.barcodeUsedBy)

        vm.edit { copy(barcode = "599", energy = "350") }
        vm.save()
        assertEquals("599", foods.saved.single().barcode)
    }

    @Test
    fun `a food's own barcode isn't a clash`() {
        val vm = editor(FoodEditorKey(foodId = "cheese"))

        assertNull(vm.state.barcodeUsedBy)
    }
}

private class EditorFoods(foods: List<Food>) : FoodRepository {
    val foods = MutableStateFlow(foods)
    val saved = mutableListOf<Food>()

    override fun search(query: String): Flow<List<Food>> = foods.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }
    override fun observe(id: String) = foods.map { list -> list.find { it.id == id } }
    override suspend fun get(id: String) = foods.value.find { it.id == id }
    override suspend fun byBarcode(barcode: String) = foods.value.find { it.barcode == barcode }
    override suspend fun bySource(source: FoodSource, sourceRef: String) = foods.value.find { it.source == source && it.sourceRef == sourceRef }
    override suspend fun save(food: Food): Food {
        saved += food
        foods.update { list -> list.filterNot { it.id == food.id } + food }
        return food
    }
    override suspend fun delete(id: String) = foods.update { list -> list.filterNot { it.id == id } }
}
