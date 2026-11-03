package dev.southstar21.kaloria.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.WeightEntry
import java.time.Instant

// (foodId, deleted, loggedAt) covers food search's "last logged" lookup, so it never reads the rows.
@Entity(tableName = "log_entries", indices = [Index("loggedAt"), Index("foodId", "deleted", "loggedAt")])
data class LogEntryEntity(
    @PrimaryKey override val id: String,
    val foodId: String?,
    val foodName: String,
    val brand: String?,
    val amount: Double,
    val unit: AmountUnit,
    @Embedded(prefix = "per100_") val nutrientsPer100: NutrientColumns,
    val loggedAt: Long,
    val note: String?,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncedEntity

fun LogEntryEntity.asExternalModel() = LogEntry(
    id = id,
    foodId = foodId,
    foodName = foodName,
    brand = brand,
    amount = amount,
    unit = unit,
    nutrientsPer100 = nutrientsPer100.asExternalModel(),
    loggedAt = Instant.ofEpochMilli(loggedAt),
    note = note,
)

fun LogEntry.asEntity(updatedAt: Long) = LogEntryEntity(
    id = id,
    foodId = foodId,
    foodName = foodName,
    brand = brand,
    amount = amount,
    unit = unit,
    nutrientsPer100 = nutrientsPer100.asColumns(),
    loggedAt = loggedAt.toEpochMilli(),
    note = note,
    updatedAt = updatedAt,
)

@Entity(tableName = "weight_entries", indices = [Index("loggedAt")])
data class WeightEntryEntity(
    @PrimaryKey override val id: String,
    val kilograms: Double,
    val loggedAt: Long,
    /** Set when the weigh-in came from another app through Health Connect; that record's id. */
    @ColumnInfo(defaultValue = "NULL") val healthConnectId: String? = null,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncedEntity

fun WeightEntryEntity.asExternalModel() = WeightEntry(id, kilograms, Instant.ofEpochMilli(loggedAt), fromHealth = healthConnectId != null)

/** A single row; its [id] becomes the user's one record in the PocketBase `goals` collection. */
@Entity(tableName = "goals")
data class GoalsEntity(
    @PrimaryKey override val id: String,
    val energyKcal: Int?,
    val protein: Int?,
    val carbohydrates: Int?,
    val fat: Int?,
    val fiber: Int?,
    val steps: Int?,
    val micros: Map<String, Double>,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncedEntity

fun GoalsEntity.asExternalModel() = Goals(
    energyKcal = energyKcal,
    protein = protein,
    carbohydrates = carbohydrates,
    fat = fat,
    fiber = fiber,
    steps = steps,
    micros = micros.mapNotNull { (key, value) -> Micronutrient.fromKey(key)?.let { it to value } }.toMap(),
)

fun Goals.asEntity(id: String, updatedAt: Long) = GoalsEntity(
    id = id,
    energyKcal = energyKcal,
    protein = protein,
    carbohydrates = carbohydrates,
    fat = fat,
    fiber = fiber,
    steps = steps,
    micros = micros.mapKeys { it.key.key },
    updatedAt = updatedAt,
)
