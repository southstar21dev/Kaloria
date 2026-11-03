package dev.southstar21.kaloria.feature.foodsearch

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodCatalog
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.OnlineResults
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.model.WeightEntry
import dev.southstar21.kaloria.core.network.OpenFoodFactsDataSource
import dev.southstar21.kaloria.core.network.UsdaDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
class FoodSearchViewModelTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val today = LocalDate.of(2026, 10, 30)
    private val clock = Clock.fixed(LocalDateTime.of(2026, 10, 30, 14, 27, 45).atZone(zone).toInstant(), zone)

    private fun food(id: String, name: String) = Food(id = id, name = name, source = FoodSource.Manual, nutrients = Nutrients(energyKcal = 100.0), updatedAt = Instant.EPOCH)

    private val foods = FakeFoodRepository(listOf(food("oats", "Oats"), food("rice", "Rice"), food("oat-milk", "Oat milk")))
    private val diary = FakeDiaryRepository()

    /** No network in tests: every online request fails, which the catalog reports per source. */
    private val offline = HttpClient(MockEngine { error("no network in tests") })
    private val catalog = FoodCatalog(
        context = ApplicationProvider.getApplicationContext<Context>(),
        off = OpenFoodFactsDataSource(offline),
        usda = UsdaDataSource(offline, "test-key"),
        foods = foods,
        clock = clock,
    )

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(day: LocalDate = today) = FoodSearchViewModel(FoodSearchKey(day.toEpochDay()), foods, catalog, diary, clock)

    private fun FoodSearchViewModel.type(text: String) {
        query = text
        // Outside composition nothing sends snapshot apply notifications, which snapshotFlow needs.
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `local results come after a short debounce, for the trimmed query`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.results.collect {} }

        runCurrent()
        assertNull(vm.results.value) // still loading
        advanceTimeBy(151)
        assertEquals(listOf("oats", "rice", "oat-milk"), vm.results.value?.map { it.id })

        vm.type("  oat ")
        runCurrent()
        advanceTimeBy(100)
        assertEquals(3, vm.results.value?.size) // not yet: still debouncing
        advanceTimeBy(51)
        assertEquals(listOf("oats", "oat-milk"), vm.results.value?.map { it.id })
        assertEquals(listOf("", "oat"), foods.searches)
    }

    @Test
    fun `typing quickly searches only once it settles`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.results.collect {} }
        advanceTimeBy(151)

        for (text in listOf("r", "ri", "ric", "rice")) {
            vm.type(text)
            runCurrent()
            advanceTimeBy(50)
        }
        advanceTimeBy(151)

        assertEquals(listOf("", "rice"), foods.searches)
        assertEquals(listOf("rice"), vm.results.value?.map { it.id })
    }

    @Test
    fun `short queries don't search online`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.online.collect {} }

        vm.type("oa")
        runCurrent()
        advanceTimeBy(1_000)

        assertEquals(OnlineState.Idle, vm.online.value)
    }

    @Test
    fun `longer queries search online and report failed sources`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.online.collect {} }

        vm.type("oats")
        val loaded = vm.online.first { it is OnlineState.Loaded } as OnlineState.Loaded

        assertEquals(OnlineResults(failed = setOf(FoodSource.OpenFoodFacts, FoodSource.Usda)), loaded.results)
    }

    @Test
    fun `logging an own food logs it on the screen's day at the chosen time`() = runTest {
        val day = today.minusDays(2)
        val vm = viewModel(day)
        val oats = foods.get("oats")!!

        vm.log(Pickable.Own(oats), amount = 60.0, time = LocalTime.of(8, 30))
        runCurrent()

        val entry = diary.entries.value.single()
        assertEquals("oats", entry.foodId)
        assertEquals("Oats", entry.foodName)
        assertEquals(60.0, entry.amount, 0.0)
        assertEquals(day.atTime(8, 30).atZone(zone).toInstant(), entry.loggedAt)
        assertEquals("Oats", vm.logged.first())
    }

    @Test
    fun `logging an online food imports it once`() = runTest {
        val vm = viewModel()
        val remote = RemoteFood(
            source = FoodSource.OpenFoodFacts,
            sourceRef = "5998200467004",
            name = "Trappista",
            brand = "Coop",
            barcode = "5998200467004",
            unit = AmountUnit.Gram,
            nutrients = Nutrients(energyKcal = 320.0),
            servings = listOf(Serving("slice", 20.0)),
        )

        vm.log(Pickable.Online(remote), amount = 40.0, time = LocalTime.NOON)
        vm.log(Pickable.Online(remote), amount = 20.0, time = LocalTime.NOON)
        runCurrent()

        val imported = foods.foods.value.filter { it.source == FoodSource.OpenFoodFacts }
        assertEquals(1, imported.size)
        assertEquals("Trappista", imported.single().name)
        assertEquals(listOf(Serving("slice", 20.0)), imported.single().servings)
        assertEquals(listOf(imported.single().id, imported.single().id), diary.entries.value.map { it.foodId })
    }

    @Test
    fun `default time is now on today and noon on other days`() {
        assertEquals(LocalTime.of(14, 27), viewModel(today).defaultTime())
        assertEquals(LocalTime.NOON, viewModel(today.minusDays(1)).defaultTime())
    }

    @Test
    fun `saving servings updates the food`() = runTest {
        val vm = viewModel()

        vm.saveServings("rice", listOf(Serving("cup", 185.0)))
        runCurrent()

        assertEquals(listOf(Serving("cup", 185.0)), foods.get("rice")!!.servings)
        assertTrue(diary.entries.value.isEmpty())
    }
}

private class FakeFoodRepository(foods: List<Food>) : FoodRepository {
    val foods = MutableStateFlow(foods)
    val searches = mutableListOf<String>()

    override fun search(query: String): Flow<List<Food>> {
        searches += query
        return foods.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }
    }
    override fun observe(id: String) = foods.map { list -> list.find { it.id == id } }
    override suspend fun get(id: String) = foods.value.find { it.id == id }
    override suspend fun byBarcode(barcode: String) = foods.value.find { it.barcode == barcode }
    override suspend fun bySource(source: FoodSource, sourceRef: String) = foods.value.find { it.source == source && it.sourceRef == sourceRef }
    override suspend fun save(food: Food): Food {
        foods.update { list -> list.filterNot { it.id == food.id } + food }
        return food
    }
    override suspend fun delete(id: String) = foods.update { list -> list.filterNot { it.id == id } }
}

/** Only what food search uses: saving entries. */
private class FakeDiaryRepository : DiaryRepository {
    val entries = MutableStateFlow<List<LogEntry>>(emptyList())

    override suspend fun saveEntry(entry: LogEntry) = entries.update { it + entry }
    override fun entries(day: LocalDate): Flow<List<LogEntry>> = entries
    override fun entries(from: LocalDate, toInclusive: LocalDate): Flow<List<LogEntry>> = entries
    override suspend fun entry(id: String) = entries.value.find { it.id == id }
    override suspend fun deleteEntry(id: String) = entries.update { list -> list.filterNot { it.id == id } }
    override fun loggedDays(): Flow<Set<LocalDate>> = MutableStateFlow(emptySet())
    override fun weights(from: LocalDate, toInclusive: LocalDate): Flow<List<WeightEntry>> = MutableStateFlow(emptyList())
    override fun latestWeight(): Flow<WeightEntry?> = MutableStateFlow(null)
    override suspend fun addWeight(kilograms: Double, at: Instant) = Unit
    override suspend fun deleteWeight(id: String) = Unit
}
