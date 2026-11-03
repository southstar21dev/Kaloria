package dev.southstar21.kaloria.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.southstar21.kaloria.core.database.model.FoodEntity
import dev.southstar21.kaloria.core.database.model.GoalsEntity
import dev.southstar21.kaloria.core.database.model.LogEntryEntity
import dev.southstar21.kaloria.core.database.model.NutrientColumns
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.FoodSource

internal fun inMemoryDatabase(): KaloriaDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KaloriaDatabase::class.java)
    .allowMainThreadQueries()
    .build()

internal val someNutrients = NutrientColumns(100.0, null, null, null, null, null, null, null, emptyMap())

internal fun food(
    id: String,
    name: String,
    brand: String? = null,
    barcode: String? = null,
    favorite: Boolean = false,
    updatedAt: Long = 1L,
    deleted: Boolean = false,
    dirty: Boolean = true,
) = FoodEntity(
    id = id, name = name, brand = brand, barcode = barcode, source = FoodSource.Manual, sourceRef = null,
    unit = AmountUnit.Gram, nutrients = someNutrients, servings = emptyList(), favorite = favorite,
    updatedAt = updatedAt, deleted = deleted, dirty = dirty,
)

internal fun entry(id: String, loggedAt: Long, foodId: String? = null, updatedAt: Long = 1L, deleted: Boolean = false, dirty: Boolean = true) = LogEntryEntity(
    id = id, foodId = foodId, foodName = "food", brand = null, amount = 100.0, unit = AmountUnit.Gram,
    nutrientsPer100 = someNutrients, loggedAt = loggedAt, note = null, updatedAt = updatedAt, deleted = deleted, dirty = dirty,
)

internal fun weight(
    id: String,
    kilograms: Double = 80.0,
    loggedAt: Long = 1_000L,
    healthConnectId: String? = null,
    updatedAt: Long = 1L,
    deleted: Boolean = false,
    dirty: Boolean = true,
) = WeightEntryEntity(id, kilograms, loggedAt, healthConnectId, updatedAt, deleted, dirty)

internal fun goals(id: String, energyKcal: Int? = 2000, updatedAt: Long = 1L) = GoalsEntity(id, energyKcal, null, null, null, null, null, emptyMap(), updatedAt)
