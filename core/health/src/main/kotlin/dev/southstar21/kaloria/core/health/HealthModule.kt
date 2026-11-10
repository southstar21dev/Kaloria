package dev.southstar21.kaloria.core.health

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthModule {
    @Binds abstract fun weights(impl: HealthConnectWeights): HealthWeights
}
