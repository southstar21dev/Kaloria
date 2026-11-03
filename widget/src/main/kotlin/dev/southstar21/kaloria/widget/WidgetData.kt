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
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun diary(): DiaryRepository
    fun goals(): GoalsRepository
    fun clock(): Clock
}

internal fun Context.widgetEntryPoint() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal data class WidgetData(val totals: Nutrients, val goals: Goals)

/** Today's totals and the goals, for the day it is when the widget is drawn. */
internal suspend fun WidgetEntryPoint.todayNow(): WidgetData {
    val day = LocalDate.now(clock())
    return WidgetData(diary().entries(day).first().map { it.nutrients }.sum(), goals().goals.first())
}
