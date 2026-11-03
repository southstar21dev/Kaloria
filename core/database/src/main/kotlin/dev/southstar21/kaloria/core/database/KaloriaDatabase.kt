package dev.southstar21.kaloria.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.dao.GoalsDao
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import dev.southstar21.kaloria.core.database.model.FoodEntity
import dev.southstar21.kaloria.core.database.model.GoalsEntity
import dev.southstar21.kaloria.core.database.model.LogEntryEntity
import dev.southstar21.kaloria.core.database.model.WeightEntryEntity

@Database(
    entities = [FoodEntity::class, LogEntryEntity::class, WeightEntryEntity::class, GoalsEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // 2: indexes for food search and imports
        AutoMigration(from = 2, to = 3, spec = KaloriaDatabase.RemoveWater::class), // 3: water tracking removed
        AutoMigration(from = 3, to = 4), // 4: weight_entries.healthConnectId
    ],
)
@TypeConverters(Converters::class)
abstract class KaloriaDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun weightDao(): WeightDao
    abstract fun goalsDao(): GoalsDao

    @DeleteTable(tableName = "water_entries")
    @DeleteColumn(tableName = "goals", columnName = "waterMl")
    class RemoveWater : AutoMigrationSpec
}
