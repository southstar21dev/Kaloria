package dev.southstar21.kaloria.core.data.widget

/**
 * Asks the home-screen widget to redraw because today's totals or the goals may have changed.
 * Bound by :widget; calls in quick succession are merged there.
 */
fun interface WidgetUpdater {
    fun requestUpdate()
}
