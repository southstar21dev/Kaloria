package dev.southstar21.kaloria.feature.today

import androidx.activity.result.contract.ActivityResultContract
import dev.southstar21.kaloria.core.data.health.StepsSource
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.WeightEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A clock the test can move. */
class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

class FakeDiaryRepository(private val zone: ZoneId) : DiaryRepository {
    val entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val deleted = mutableListOf<String>()

    override fun entries(day: LocalDate) = entries(day, day)
    override fun entries(from: LocalDate, toInclusive: LocalDate): Flow<List<LogEntry>> = entries.map { list ->
        list.filter { it.loggedAt.atZone(zone).toLocalDate() in from..toInclusive }.sortedBy { it.loggedAt }
    }
    override suspend fun entry(id: String) = entries.value.find { it.id == id }
    override suspend fun saveEntry(entry: LogEntry) = entries.update { list -> list.filterNot { it.id == entry.id } + entry }
    override suspend fun deleteEntry(id: String) {
        deleted += id
        entries.update { list -> list.filterNot { it.id == id } }
    }
    override fun loggedDays(): Flow<Set<LocalDate>> = entries.map { list -> list.mapTo(HashSet()) { it.loggedAt.atZone(zone).toLocalDate() } }
    override fun weights(from: LocalDate, toInclusive: LocalDate): Flow<List<WeightEntry>> = MutableStateFlow(emptyList())
    override fun latestWeight(): Flow<WeightEntry?> = MutableStateFlow(null)
    override suspend fun addWeight(kilograms: Double, at: Instant) = Unit
    override suspend fun deleteWeight(id: String) = Unit
}

class FakeGoalsRepository(initial: Goals = Goals()) : GoalsRepository {
    override val goals = MutableStateFlow(initial)
    override suspend fun save(goals: Goals) {
        this.goals.value = goals
    }
}

class FakeFoodRepository(foods: List<Food> = emptyList()) : FoodRepository {
    val foods = MutableStateFlow(foods)
    val searches = mutableListOf<String>()

    override fun search(query: String): Flow<List<Food>> {
        searches += query
        return foods.map { list -> list.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) } }
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

class FakeStepsSource(var access: HealthAccess = HealthAccess.Granted, val perDay: MutableMap<LocalDate, Long> = mutableMapOf()) : StepsSource {
    val requestedDays = mutableListOf<LocalDate>()

    override val permissions: Set<String> = setOf("steps")
    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> = throw UnsupportedOperationException()
    override suspend fun access() = access
    override suspend fun stepsPerDay(from: LocalDate, toInclusive: LocalDate) = if (access == HealthAccess.Granted) perDay.filterKeys { it in from..toInclusive } else emptyMap()
    override suspend fun steps(day: LocalDate): Long? {
        requestedDays += day
        return perDay[day]
    }
}
