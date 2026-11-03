package dev.southstar21.kaloria.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One bar per day. Days without data (null) leave a gap rather than a zero bar; bars over [goal]
 * use [overColor] (an accent, not an error: going over isn't a failure). The dashed line marks
 * the goal. [labels] may contain blanks; each is centred under its bar at its natural width.
 */
@Composable
fun BarChart(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    overColor: Color = MaterialTheme.colorScheme.tertiary,
    height: Dp = 140.dp,
    labels: List<String> = emptyList(),
    description: String,
) {
    val progress = remember(values) { Animatable(0f) }
    // Bars grow one after another, left to right, rather than all at once.
    LaunchedEffect(values) { progress.animateTo(1f, androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Worked out once, not on each of the animation's frames.
    val top = remember(values, goal) { (values.filterNotNull() + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 }?.times(1.1) ?: 1.1 }
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            val barWidth = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barWidth) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2))
                if (v != null && v > 0) {
                    val stagger = 0.5f
                    val start = i.toFloat() / values.size.coerceAtLeast(1) * stagger
                    val local = ((progress.value - start) / (1f - stagger)).coerceIn(0f, 1f)
                    val eased = 1f - (1f - local) * (1f - local)
                    val h = (v / top * size.height * eased).toFloat()
                    drawRoundRect(
                        if (goal != null && v > goal) overColor else color,
                        Offset(x, size.height - h),
                        Size(barWidth, h),
                        CornerRadius(barWidth / 2),
                    )
                }
            }
            goal?.let {
                val y = (size.height - it / top * size.height).toFloat()
                drawLine(goalColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
        }
        if (labels.isNotEmpty()) AxisLabels(labels, slots = values.size)
    }
}

/** A line through dated points, x spaced by [xs] (0..1). Used for weight over time. */
@Composable
fun LineChart(
    xs: List<Float>,
    ys: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 140.dp,
    valueLabel: (Double) -> String = { "%.1f".format(it) },
    description: String,
) {
    val progress = remember(ys) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(ys) { progress.animateTo(1f, spec) }
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(modifier.semantics { contentDescription = description }) {
        if (ys.isNotEmpty()) {
            Text(
                valueLabel(ys.max()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(androidx.compose.ui.Alignment.End),
            )
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (ys.isEmpty()) return@Canvas
            val min = ys.min()
            val max = ys.max()
            val span = (max - min).takeIf { it > 0.1 } ?: 1.0
            fun point(i: Int) = Offset(
                xs[i] * size.width,
                (size.height * 0.9f - ((ys[i] - min) / span * size.height * 0.8f)).toFloat(),
            )
            repeat(3) { r ->
                val y = size.height * (0.1f + 0.4f * r)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val path = Path()
            val drawn = (ys.size * progress.value).toInt().coerceIn(1, ys.size)
            for (i in 0 until drawn) {
                val p = point(i)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0 until drawn) drawCircle(color, 4.dp.toPx(), point(i))
        }
        if (ys.isNotEmpty()) {
            Text(
                valueLabel(ys.min()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(androidx.compose.ui.Alignment.End),
            )
        }
    }
}

/** Labels centred under their bar's slot, at natural width and kept inside the chart. Blanks get no node. */
@Composable
private fun AxisLabels(labels: List<String>, slots: Int) {
    val shown = remember(labels) { labels.indices.filter { labels[it].isNotBlank() } }
    androidx.compose.ui.layout.Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            shown.forEach { index ->
                val it = labels[index]
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints()) }
        val width = constraints.maxWidth
        val slot = width.toFloat() / slots.coerceAtLeast(1)
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { n, p ->
                val i = shown[n]
                val x = (slot * i + slot / 2 - p.width / 2f).toInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.placeRelative(x, 0)
            }
        }
    }
}
