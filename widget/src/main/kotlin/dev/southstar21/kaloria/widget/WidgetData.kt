package dev.southstar21.kaloria.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.southstar21.kaloria.core.data.repository.DiaryRepository
import dev.southstar21.kaloria.core.data.repository.GoalsRepository
import dev.southstar21.kaloria.core.model.Goals
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.sum
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun diary(): DiaryRepository
    fun goals(): GoalsRepository
    fun clock(): Clock
}

internal fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal data class WidgetData(val totals: Nutrients, val goals: Goals)

/** A day's totals and the goals, as they change. */
internal fun WidgetEntryPoint.dayData(day: LocalDate): Flow<WidgetData> = combine(diary().entries(day), goals().goals) { entries, goals -> WidgetData(entries.map { it.nutrients }.sum(), goals) }
