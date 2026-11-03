package dev.southstar21.kaloria.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.data.widget.WidgetUpdater
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Redraws the widget after writes, from the app or a sync. Pushed from the write path rather than
 * by watching the database, so nothing stays subscribed while the app is idle.
 */
@Singleton
internal class GlanceWidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) : WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    @Synchronized
    override fun requestUpdate() {
        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS) // a burst of writes (e.g. a sync) redraws once
            runCatching { KaloriaWidget().updateAll(context) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
