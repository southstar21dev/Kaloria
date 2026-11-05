package dev.southstar21.kaloria.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.southstar21.kaloria.core.designsystem.component.grouped
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaColors
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The app's own deep-ink palette, so the widget follows neither the wallpaper nor the system theme. */
private val KaloriaWidgetColors = ColorProviders(KaloriaColors)

/** Intent action that opens the barcode scanner directly (handled by MainActivity). */
const val ACTION_SCAN_BARCODE = "dev.southstar21.kaloria.action.SCAN_BARCODE"
const val ACTION_SEARCH_FOOD = "dev.southstar21.kaloria.action.SEARCH_FOOD"

/**
 * Drawn from a read of today's data. Writes push a redraw (see GlanceWidgetUpdater) and an alarm
 * redraws it at midnight, so it never needs to wake up on a timer. While a session is alive the
 * data is watched, because a redraw pushed then recomposes that session instead of starting anew.
 */
class KaloriaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val day = LocalDate.now(entryPoint.clock())
        val initial = entryPoint.dayData(day).first()
        NewDayAlarm.schedule(context, entryPoint.clock())
        provideContent {
            val data by remember { entryPoint.dayData(day) }.collectAsState(initial)
            GlanceTheme(colors = KaloriaWidgetColors) { Content(context, data) }
        }
    }
}

class KaloriaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KaloriaWidget()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NewDayAlarm.ACTION, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        glanceAppWidget.updateAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }

            else -> super.onReceive(context, intent)
        }
    }
}

@Composable
private fun Content(context: Context, data: WidgetData) {
    val colors = GlanceTheme.colors
    val eaten = data.totals.energyKcal ?: 0.0
    val goal = data.goals.energyKcal
    val remaining = goal?.let { it - eaten }
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(colors.widgetBackground)
            .padding(16.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                abs(remaining ?: eaten).roundToInt().let { it.grouped() },
                style = TextStyle(color = colors.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                context.getString(
                    when {
                        remaining == null -> R.string.widget_kcal_eaten
                        remaining >= 0 -> R.string.widget_kcal_left
                        else -> R.string.widget_kcal_over
                    },
                ),
                style = TextStyle(color = if (remaining != null && remaining < 0) colors.error else colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
        }
        Text(
            context.getString(
                R.string.widget_macros,
                (data.totals.protein ?: 0.0).roundToInt(),
                (data.totals.carbohydrates ?: 0.0).roundToInt(),
                (data.totals.fat ?: 0.0).roundToInt(),
            ),
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            Pill(context.getString(R.string.widget_scan), colors.primary, colors.onPrimary, GlanceModifier.defaultWeight()) {
                actionStartActivity(shortcutIntent(context, ACTION_SCAN_BARCODE))
            }
            Spacer(GlanceModifier.width(8.dp))
            Pill(context.getString(R.string.widget_search), colors.secondaryContainer, colors.onSecondaryContainer, GlanceModifier.defaultWeight()) {
                actionStartActivity(shortcutIntent(context, ACTION_SEARCH_FOOD))
            }
        }
    }
}

/** Brings back the running app (onNewIntent) rather than recreating it, or starts it if it isn't running. */
private fun shortcutIntent(context: Context, action: String) = Intent(action)
    .setPackage(context.packageName)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

@Composable
private fun Pill(
    label: String,
    background: ColorProvider,
    content: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    action: () -> androidx.glance.action.Action,
) {
    Box(
        modifier.height(44.dp).cornerRadius(22.dp).background(background).padding(horizontal = 12.dp).clickable(action()),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}
