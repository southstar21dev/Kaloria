package dev.southstar21.kaloria.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.southstar21.kaloria.core.data.health.HealthConnectSteps
import dev.southstar21.kaloria.core.data.health.StepsSource
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.FoodRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.data.repository.OfflineFirstDiaryRepository
import dev.southstar21.kaloria.core.data.repository.OfflineFirstFoodRepository
import dev.southstar21.kaloria.core.data.repository.OfflineFirstGoalsRepository
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds abstract fun foods(impl: OfflineFirstFoodRepository): FoodRepository

    @Binds abstract fun diary(impl: OfflineFirstDiaryRepository): DiaryRepository

    @Binds abstract fun goals(impl: OfflineFirstGoalsRepository): GoalsRepository

    @Binds abstract fun steps(impl: HealthConnectSteps): StepsSource

    @Binds abstract fun timeZones(impl: BroadcastTimeZoneMonitor): TimeZoneMonitor

    companion object {
        @Provides fun clock(): Clock = CurrentZoneClock
    }
}
