package dev.southstar21.kaloria.widget

import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The day the widget shows. A running widget session reads it while composing, so a redraw after
 * midnight moves that session to the new day too rather than keeping the day it started with.
 * Moved on before every redraw: the midnight alarm, time and zone changes, and writes.
 */
internal object WidgetDay {
    private val day = MutableStateFlow<LocalDate?>(null)

    val current: StateFlow<LocalDate?> = day.asStateFlow()

    fun refresh(clock: Clock): LocalDate = LocalDate.now(clock).also { day.value = it }
}
