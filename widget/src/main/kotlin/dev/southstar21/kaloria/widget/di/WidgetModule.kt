package dev.southstar21.kaloria.widget.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import dev.southstar21.kaloria.widget.GlanceWidgetUpdater

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {
    @Binds abstract fun widgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
}
