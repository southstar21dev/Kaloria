package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.WaterDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.database.model.WaterEntryEntity
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.database.model.asEntity
import dev.southstar21.kaloria.core.database.model.asExternalModel
import dev.southstar21.kaloria.core.model.Ids
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.WaterEntry
import dev.southstar21.kaloria.core.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Food log, water and weight: everything timestamped that makes up a day. */
interface DiaryRepository {
    fun entries(day: LocalDate): Flow<List<LogEntry>>
    fun entries(from: LocalDate, toInclusive: LocalDate): Flow<List<LogEntry>>
    suspend fun entry(id: String): LogEntry?
    suspend fun saveEntry(entry: LogEntry)
    suspend fun deleteEntry(id: String)

    fun water(day: LocalDate): Flow<List<WaterEntry>>
    suspend fun addWater(millilitres: Int, at: Instant)
    suspend fun deleteWater(id: String)

    fun weights(from: LocalDate, toInclusive: LocalDate): Flow<List<WeightEntry>>
    fun latestWeight(): Flow<WeightEntry?>
    suspend fun addWeight(kilograms: Double, at: Instant)
    suspend fun deleteWeight(id: String)
}

internal class OfflineFirstDiaryRepository @Inject constructor(
    private val logDao: LogEntryDao,
    private val waterDao: WaterDao,
    private val weightDao: WeightDao,
    private val clock: Clock,
    private val sync: SyncRequester,
) : DiaryRepository {

    private fun LocalDate.startMillis() = atStartOfDay(clock.zone).toInstant().toEpochMilli()

    override fun entries(day: LocalDate) = entries(day, day)

    override fun entries(from: LocalDate, toInclusive: LocalDate) =
        logDao.between(from.startMillis(), toInclusive.plusDays(1).startMillis())
            .map { list -> list.map { it.asExternalModel() } }

    override suspend fun entry(id: String) = logDao.get(id)?.takeUnless { it.deleted }?.asExternalModel()

    override suspend fun saveEntry(entry: LogEntry) {
        logDao.upsert(entry.asEntity(updatedAt = clock.millis()))
        sync.requestSync()
    }

    override suspend fun deleteEntry(id: String) {
        logDao.softDelete(id, clock.millis())
        sync.requestSync()
    }

    override fun water(day: LocalDate) =
        waterDao.between(day.startMillis(), day.plusDays(1).startMillis()).map { list -> list.map { it.asExternalModel() } }

    override suspend fun addWater(millilitres: Int, at: Instant) {
        waterDao.upsert(WaterEntryEntity(Ids.newId(), millilitres, at.toEpochMilli(), updatedAt = clock.millis()))
        sync.requestSync()
    }

    override suspend fun deleteWater(id: String) {
        waterDao.softDelete(id, clock.millis())
        sync.requestSync()
    }

    override fun weights(from: LocalDate, toInclusive: LocalDate) =
        weightDao.between(from.startMillis(), toInclusive.plusDays(1).startMillis())
            .map { list -> list.map { it.asExternalModel() } }

    override fun latestWeight() = weightDao.latest().map { it?.asExternalModel() }

    override suspend fun addWeight(kilograms: Double, at: Instant) {
        weightDao.upsert(WeightEntryEntity(Ids.newId(), kilograms, at.toEpochMilli(), updatedAt = clock.millis()))
        sync.requestSync()
    }

    override suspend fun deleteWeight(id: String) {
        weightDao.softDelete(id, clock.millis())
        sync.requestSync()
    }
}

/** Logs [amount] of [food], copying its name and values into the entry. */
suspend fun DiaryRepository.logFood(food: dev.southstar21.kaloria.core.model.Food, amount: Double, at: Instant) = saveEntry(
    LogEntry(
        id = Ids.newId(),
        foodId = food.id,
        foodName = food.name,
        brand = food.brand,
        amount = amount,
        unit = food.unit,
        nutrientsPer100 = food.nutrients,
        loggedAt = at,
    ),
)
