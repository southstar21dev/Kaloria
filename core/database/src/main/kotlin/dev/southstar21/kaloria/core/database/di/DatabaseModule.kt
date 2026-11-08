package dev.southstar21.kaloria.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import dev.southstar21.kaloria.core.database.dao.FoodDao
import dev.southstar21.kaloria.core.database.dao.GoalsDao
import dev.southstar21.kaloria.core.database.dao.LogEntryDao
import dev.southstar21.kaloria.core.database.dao.WeightDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KaloriaDatabase = Room.databaseBuilder(context, KaloriaDatabase::class.java, "kaloria.db").build()

    @Provides fun foodDao(db: KaloriaDatabase): FoodDao = db.foodDao()

    @Provides fun logEntryDao(db: KaloriaDatabase): LogEntryDao = db.logEntryDao()

    @Provides fun weightDao(db: KaloriaDatabase): WeightDao = db.weightDao()

    @Provides fun goalsDao(db: KaloriaDatabase): GoalsDao = db.goalsDao()
}
