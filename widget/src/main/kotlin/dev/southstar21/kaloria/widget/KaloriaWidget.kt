package dev.southstar21.kaloria.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaDarkColors
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaLightColors
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** The app's own palette, so the widget doesn't follow the wallpaper either. */
private val KaloriaWidgetColors = ColorProviders(light = KaloriaLightColors, dark = KaloriaDarkColors)

/** Intent action that opens the barcode scanner directly (handled by MainActivity). */
const val ACTION_SCAN_BARCODE = "dev.southstar21.kaloria.action.SCAN_BARCODE"
const val ACTION_SEARCH_FOOD = "dev.southstar21.kaloria.action.SEARCH_FOOD"

class KaloriaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = context.widgetEntryPoint()
        val initial = entryPoint.todayNow()
        provideContent {
            val data by entryPoint.today().collectAsState(initial)
            GlanceTheme(colors = KaloriaWidgetColors) { Content(context, data) }
        }
    }
}

class KaloriaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KaloriaWidget()
}

/** +250 ml straight from the home screen, without opening the app. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.widgetEntryPoint().diary().addWater(250, Instant.now())
        KaloriaWidget().updateAll(context)
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
                abs(remaining ?: eaten).roundToInt().let { "%,d".format(it) },
                style = TextStyle(color = colors.onSurface, fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                when {
                    remaining == null -> "kcal eaten"
                    remaining >= 0 -> "kcal left"
                    else -> "kcal over"
                },
                style = TextStyle(color = if (remaining != null && remaining < 0) colors.error else colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
        }
        Text(
            listOf("P" to data.totals.protein, "C" to data.totals.carbohydrates, "F" to data.totals.fat)
                .joinToString(" · ") { (l, v) -> "$l ${(v ?: 0.0).roundToInt()}g" } +
                "  ·  💧 ${"%.2f".format(data.waterMl / 1000.0)}" + (data.goals.waterMl?.let { "/${"%.1f".format(it / 1000.0)}" } ?: "") + " L",
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            Pill("Scan", colors.primary, colors.onPrimary, GlanceModifier.defaultWeight()) {
                actionStartActivity(Intent(ACTION_SCAN_BARCODE).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            Spacer(GlanceModifier.width(8.dp))
            Pill("+250 ml", colors.secondaryContainer, colors.onSecondaryContainer) { actionRunCallback<AddWaterAction>() }
            Spacer(GlanceModifier.width(8.dp))
            Pill("Search", colors.secondaryContainer, colors.onSecondaryContainer) {
                actionStartActivity(Intent(ACTION_SEARCH_FOOD).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
    }
}

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

