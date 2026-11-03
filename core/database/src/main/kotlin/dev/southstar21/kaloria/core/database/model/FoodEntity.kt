package dev.southstar21.kaloria.core.database.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Food
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Serving
import java.time.Instant

@Entity(tableName = "foods", indices = [Index("barcode"), Index("name"), Index("source", "sourceRef")])
data class FoodEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val source: FoodSource,
    val sourceRef: String?,
    val unit: AmountUnit,
    @Embedded val nutrients: NutrientColumns,
    val servings: List<Serving>,
    val favorite: Boolean,
    override val updatedAt: Long,
    override val deleted: Boolean = false,
    override val dirty: Boolean = true,
) : SyncedEntity

fun FoodEntity.asExternalModel() = Food(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    source = source,
    sourceRef = sourceRef,
    unit = unit,
    nutrients = nutrients.asExternalModel(),
    servings = servings,
    favorite = favorite,
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Food.asEntity(dirty: Boolean = true) = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    barcode = barcode,
    source = source,
    sourceRef = sourceRef,
    unit = unit,
    nutrients = nutrients.asColumns(),
    servings = servings,
    favorite = favorite,
    updatedAt = updatedAt.toEpochMilli(),
    dirty = dirty,
)
