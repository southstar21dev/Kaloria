package dev.southstar21.kaloria.feature.foodsearch

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
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
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The portion sheet's food, the day it logs onto, and what the online results look like. */
@RunWith(RobolectricTestRunner::class)
class FoodSearchPickTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private val clock = MovableClock(LocalDateTime.of(2026, 10, 30, 23, 58).atZone(zone).toInstant(), zone)

    private val oats = Food(id = "oats", name = "Oats", source = FoodSource.Manual, nutrients = Nutrients(energyKcal = 380.0), updatedAt = Instant.EPOCH)
    private val foods = PickFoodRepository(listOf(oats))
    private val diary = PickDiaryRepository()

    /** Open Food Facts answers every search with the same product twice; USDA with nothing. */
    private var onlineRequests = 0
    private val client = HttpClient(
        MockEngine { request ->
            onlineRequests++
            val body = if ("openfoodfacts" in request.url.host) {
                """{"hits":[$TRAPPISTA,$TRAPPISTA]}"""
            } else {
                """{"foods":[]}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private val catalog = FoodCatalog(
        context = ApplicationProvider.getApplicationContext<Context>(),
        off = OpenFoodFactsDataSource(client),
        usda = UsdaDataSource(client, "test-key"),
        foods = foods,
        clock = clock,
    )

    private val remote = RemoteFood(FoodSource.OpenFoodFacts, "599", "Trappista", null, "599", AmountUnit.Gram, Nutrients(energyKcal = 357.0))

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(key: FoodSearchKey = FoodSearchKey(), saved: SavedStateHandle = SavedStateHandle()) = FoodSearchViewModel(key, saved, foods, catalog, diary, clock)

    private fun FoodSearchViewModel.type(text: String) {
        query = text
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `the same product twice in one answer is listed once`() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.online.collect {} }

        vm.type("trappista")
        val loaded = vm.online.first { it is OnlineState.Loaded } as OnlineState.Loaded

        assertEquals(listOf("599"), loaded.results.openFoodFacts.map { it.sourceRef })
    }

    @Test
    fun `duplicates are dropped per source`() {
        val usda = remote.copy(source = FoodSource.Usda, sourceRef = "1")
        val results = OnlineResults(openFoodFacts = listOf(remote, remote), usda = listOf(usda, usda.copy(name = "Again")))

        val unique = results.withoutDuplicates()

        assertEquals(listOf(remote), unique.openFoodFacts)
        assertEquals(listOf(usda), unique.usda)
    }

    @Test
    fun `coming back to the screen doesn't search online again for the same query`() = runTest {
        val vm = viewModel()
        val first = backgroundScope.launch { vm.online.collect {} }
        vm.type("trappista")
        vm.online.first { it is OnlineState.Loaded }
        val requests = onlineRequests

        // Away long enough for the results to be dropped, then back.
        first.cancel()
        advanceTimeBy(10_000)
        backgroundScope.launch { vm.online.collect {} }
        advanceTimeBy(1_000)

        assertEquals(requests, onlineRequests)
        assertEquals(listOf("599"), (vm.online.value as OnlineState.Loaded).results.openFoodFacts.map { it.sourceRef })
    }

    @Test
    fun `a picked online food stays picked while the results reload`() {
        val vm = viewModel()
        vm.pick(Pickable.Online(remote))

        assertEquals(Pickable.Online(remote), vm.pickIn(emptyList(), OnlineState.Loading))
        assertEquals(Pickable.Online(remote), vm.pickIn(null, OnlineState.Idle))

        vm.pick(null)
        assertNull(vm.pickIn(emptyList(), OnlineState.Loaded(OnlineResults(openFoodFacts = listOf(remote)))))
    }

    @Test
    fun `after process death the pick is found again in the results`() {
        val saved = SavedStateHandle()
        viewModel(saved = saved).pick(Pickable.Online(remote))

        val restored = viewModel(saved = saved)

        assertNull(restored.pickIn(emptyList(), OnlineState.Loading))
        assertEquals(Pickable.Online(remote), restored.pickIn(emptyList(), OnlineState.Loaded(OnlineResults(openFoodFacts = listOf(remote)))))
    }

    @Test
    fun `a picked own food is the current one, with servings saved since`() {
        val vm = viewModel()
        vm.pick(Pickable.Own(oats))
        val updated = oats.copy(servings = listOf(Serving("cup", 80.0)))

        assertEquals(Pickable.Own(updated), vm.pickIn(listOf(updated), OnlineState.Idle))
    }

    @Test
    fun `a search opened for today logs onto the new day after midnight`() = runTest {
        val vm = viewModel(FoodSearchKey())
        assertEquals(LocalTime.of(23, 58), vm.defaultTime())

        clock.now = LocalDateTime.of(2026, 10, 31, 0, 3).atZone(zone).toInstant()
        vm.log(Pickable.Own(oats), amount = 50.0, time = LocalTime.of(0, 3))
        runCurrent()

        assertEquals(LocalDate.of(2026, 10, 31).atTime(0, 3).atZone(zone).toInstant(), diary.entries.value.single().loggedAt)
        assertEquals(LocalDate.of(2026, 10, 31), vm.day())
    }

    @Test
    fun `a search opened for a past day keeps logging onto it`() = runTest {
        val day = LocalDate.of(2026, 10, 28)
        val vm = viewModel(FoodSearchKey(day.toEpochDay()))
        clock.now = LocalDateTime.of(2026, 10, 31, 0, 3).atZone(zone).toInstant()

        vm.log(Pickable.Own(oats), amount = 50.0, time = LocalTime.NOON)
        runCurrent()

        assertEquals(day.atTime(12, 0).atZone(zone).toInstant(), diary.entries.value.single().loggedAt)
        assertEquals(LocalTime.NOON, vm.defaultTime())
    }

    private companion object {
        const val TRAPPISTA = """{"code":"599","product_name":"Trappista","nutriments":{"energy-kcal_100g":357}}"""
    }
}

/** A clock the test can move. */
private class MovableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)
    override fun instant(): Instant = now
}

private class PickFoodRepository(foods: List<Food>) : FoodRepository {
    val foods = MutableStateFlow(foods)

    override fun search(query: String): Flow<List<Food>> = foods.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }
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

private class PickDiaryRepository : DiaryRepository {
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
