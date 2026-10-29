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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun diary(): DiaryRepository
    fun goals(): GoalsRepository
}

internal fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal data class WidgetData(val totals: Nutrients, val waterMl: Int, val goals: Goals)

internal fun WidgetEntryPoint.today(): Flow<WidgetData> {
    val day = LocalDate.now()
    return combine(diary().entries(day), diary().water(day), goals().goals) { entries, water, goals ->
        WidgetData(entries.map { it.nutrients }.sum(), water.sumOf { it.millilitres }, goals)
    }
}

internal suspend fun WidgetEntryPoint.todayNow(): WidgetData = today().first()
