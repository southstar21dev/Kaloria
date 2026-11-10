package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.data.di.TimeZoneMonitor
import dev.southstar21.kaloria.core.data.sync.SyncRequester
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.database.model.asEntity
import dev.southstar21.kaloria.core.database.model.asExternalModel
import dev.southstar21.kaloria.core.model.Ids
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.WeightEntry
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Food log and weight: everything timestamped that makes up a day. */
interface DiaryRepository {
    fun entries(day: LocalDate): Flow<List<LogEntry>>
    fun entries(from: LocalDate, toInclusive: LocalDate): Flow<List<LogEntry>>
    suspend fun entry(id: String): LogEntry?
    suspend fun saveEntry(entry: LogEntry)
    suspend fun deleteEntry(id: String)

    /** Every day with at least one entry, in the device's time zone. */
    fun loggedDays(): Flow<Set<LocalDate>>

    fun weights(from: LocalDate, toInclusive: LocalDate): Flow<List<WeightEntry>>
    fun latestWeight(): Flow<WeightEntry?>
    suspend fun addWeight(kilograms: Double, at: Instant)
    suspend fun deleteWeight(id: String)
}

internal class OfflineFirstDiaryRepository @Inject constructor(
    private val logDao: LogEntryDao,
    private val weightDao: WeightDao,
    private val clock: Clock,
    private val sync: SyncRequester,
    private val widget: WidgetUpdater,
    private val zones: TimeZoneMonitor,
) : DiaryRepository {

    private fun LocalDate.startMillis(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()

    override fun entries(day: LocalDate) = entries(day, day)

    // Bounds follow the zone, so a screen kept open across a zone change moves to the new midnight.
    override fun entries(from: LocalDate, toInclusive: LocalDate) = zones.zone()
        .flatMapLatest { zone -> logDao.between(from.startMillis(zone), toInclusive.plusDays(1).startMillis(zone)) }
        .map { list -> list.map { it.asExternalModel() } }
        .flowOn(Dispatchers.Default)

    override suspend fun entry(id: String) = logDao.get(id)?.takeUnless { it.deleted }?.asExternalModel()

    override suspend fun saveEntry(entry: LogEntry) {
        // Text longer than the server accepts would be rejected on every push; it's cut to fit.
        val fitted = entry.copy(
            foodName = entry.foodName.clip(ServerLimits.NAME),
            brand = entry.brand?.clip(ServerLimits.BRAND),
            note = entry.note?.clip(ServerLimits.NOTE),
        )
        logDao.save(fitted.asEntity(updatedAt = clock.millis()))
        sync.requestSync()
        widget.requestUpdate()
    }

    override suspend fun deleteEntry(id: String) {
        logDao.softDelete(id, clock.millis())
        sync.requestSync()
        widget.requestUpdate()
    }

    override fun loggedDays() = combine(logDao.loggedQuarterHours(), zones.zone()) { quarters, zone ->
        quarters.mapTo(HashSet()) { Instant.ofEpochMilli(it * QUARTER_HOUR_MS).atZone(zone).toLocalDate() }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override fun weights(from: LocalDate, toInclusive: LocalDate) = zones.zone()
        .flatMapLatest { zone -> weightDao.between(from.startMillis(zone), toInclusive.plusDays(1).startMillis(zone)) }
        .map { list -> list.map { it.asExternalModel() } }
        .flowOn(Dispatchers.Default)

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

private const val QUARTER_HOUR_MS = 15 * 60 * 1000L

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
