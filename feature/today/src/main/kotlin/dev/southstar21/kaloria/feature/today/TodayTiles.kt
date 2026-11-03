package dev.southstar21.kaloria.feature.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.AnimatedCount
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.ProgressRing
import dev.southstar21.kaloria.core.designsystem.component.grouped
import dev.southstar21.kaloria.core.designsystem.component.roundToDisplay
import dev.southstar21.kaloria.core.model.HealthAccess
import kotlin.math.roundToInt

@Composable
private fun TileTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Calories left (or over) inside a ring. */
@Composable
internal fun CaloriesTile(state: TodayUiState, modifier: Modifier = Modifier) {
    val eaten = state.totals.energyKcal ?: 0.0
    val goal = state.goals.energyKcal
    val remaining = goal?.let { it - eaten }
    ContentCard(modifier, iridescent = true) {
        TileTitle(
            stringResource(R.string.today_calories_title),
            stringResource(
                when {
                    remaining == null -> R.string.today_eaten_so_far
                    remaining < 0 -> R.string.today_calories_over_goal
                    else -> R.string.today_calories_left
                },
            ),
        )
        Spacer(Modifier.height(12.dp))
        ProgressRing(
            progress = if (goal != null && goal > 0) (eaten / goal).toFloat() else 0f,
            modifier = Modifier.size(104.dp).align(Alignment.CenterHorizontally),
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            strokeWidth = 8.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedCount(
                    value = kotlin.math.abs(remaining ?: eaten).roundToInt(),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    format = { it.grouped() },
                )
                Text(stringResource(R.string.today_kcal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MacrosTile(state: TodayUiState, modifier: Modifier = Modifier) {
    val t = state.totals
    val g = state.goals
    ContentCard(modifier) {
        TileTitle(
            stringResource(R.string.today_macros_title),
            stringResource(if (g.protein != null || g.carbohydrates != null || g.fat != null) R.string.today_macros_of_goals else R.string.today_eaten_so_far),
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroRow(stringResource(R.string.today_protein), t.protein ?: 0.0, g.protein)
            MacroRow(stringResource(R.string.today_carbs), t.carbohydrates ?: 0.0, g.carbohydrates)
            MacroRow(stringResource(R.string.today_fat), t.fat ?: 0.0, g.fat)
        }
    }
}

@Composable
private fun MacroRow(label: String, value: Double, target: Int?) {
    val over = target != null && value > target
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                value.roundToDisplay() + (target?.let { "/$it" } ?: " g"),
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Meter(if (target != null && target > 0) (value / target).toFloat() else 0f)
    }
}

/** A rounded progress bar; full and in the accent colour once past 1. */
@Composable
private fun Meter(fraction: Float, height: androidx.compose.ui.unit.Dp = 5.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "meter")
    val color = if (fraction > 1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}

@Composable
private fun BigNumber(text: String, unit: String? = null, over: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        unit?.let { Text(" $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
    }
}

@Composable
internal fun StepsTile(steps: StepsState, goal: Int?, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    ContentCard(modifier) {
        TileTitle(
            stringResource(R.string.today_steps_title),
            goal?.let { stringResource(R.string.today_steps_of_goal, it.grouped()) } ?: stringResource(R.string.today_health_connect),
        )
        Spacer(Modifier.weight(1f).height(12.dp))
        when (steps.access) {
            HealthAccess.Granted -> {
                val count = steps.count ?: 0L
                AnimatedCount(count.toInt(), MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), format = { it.grouped() })
                Spacer(Modifier.height(6.dp))
                Meter(if (goal != null && goal > 0) count.toFloat() / goal else 0f)
            }

            HealthAccess.NeedsPermission -> FilledTonalButton(onClick = onConnect) { Text(stringResource(R.string.today_connect)) }

            else -> Text(stringResource(R.string.today_update_health_connect), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun FiberTile(value: Double, target: Int?, modifier: Modifier = Modifier) {
    ContentCard(modifier) {
        TileTitle(
            stringResource(R.string.today_fiber_title),
            target?.let { stringResource(R.string.today_fiber_of_goal, value.roundToDisplay(), it) } ?: stringResource(R.string.today_no_goal),
        )
        Spacer(Modifier.weight(1f).height(12.dp))
        BigNumber(value.roundToDisplay(), "g", over = target != null && value > target)
        Spacer(Modifier.height(6.dp))
        Meter(if (target != null && target > 0) (value / target).toFloat() else 0f)
    }
}
