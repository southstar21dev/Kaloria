package dev.southstar21.kaloria.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Pushes a widget update whenever today's totals change, from the app or a sync. */
@Singleton
class WidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) = scope.launch {
        context.widgetEntryPoint().today()
            .distinctUntilChanged()
            .drop(1)
            .debounce(500)
            .collect { KaloriaWidget().updateAll(context) }
    }
}
