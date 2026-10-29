package dev.southstar21.kaloria.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.southstar21.kaloria.core.database.KaloriaDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KaloriaDatabase =
        Room.databaseBuilder(context, KaloriaDatabase::class.java, "kaloria.db").build()

    @Provides fun foodDao(db: KaloriaDatabase) = db.foodDao()
    @Provides fun logEntryDao(db: KaloriaDatabase) = db.logEntryDao()
    @Provides fun waterDao(db: KaloriaDatabase) = db.waterDao()
    @Provides fun weightDao(db: KaloriaDatabase) = db.weightDao()
    @Provides fun goalsDao(db: KaloriaDatabase) = db.goalsDao()
}
