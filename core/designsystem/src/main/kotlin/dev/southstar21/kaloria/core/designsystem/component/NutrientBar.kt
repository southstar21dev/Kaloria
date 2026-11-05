package dev.southstar21.kaloria.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Label, "value / target unit" and a slim bar. Without a target only the value is shown. Going
 * over is shown in the accent colour, not as an error.
 */
@Composable
fun NutrientBar(
    label: String,
    value: Double,
    target: Int?,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val fraction = if (target != null && target > 0) (value / target).toFloat() else 0f
    val over = MaterialTheme.colorScheme.tertiary
    val fill = if (fraction > 1f) over else color
    val animated = animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bar",
    )
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                text = if (target != null) "${value.roundToDisplay()} / $target $unit" else "${value.roundToDisplay()} $unit",
                style = MaterialTheme.typography.labelMedium,
                color = if (fraction > 1f) over else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (target != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(fill.copy(alpha = 0.18f))
                    // Read only while drawing, so the fill grows without relayout each frame.
                    .drawBehind {
                        val width = size.width * animated.value
                        if (width > 0f) drawRoundRect(fill, size = Size(width, size.height), cornerRadius = CornerRadius(minOf(width, size.height) / 2))
                    },
            )
        }
    }
}

/** Whole numbers above 10, one decimal below: "152", "7.4". */
fun Double.roundToDisplay(): String = if (this >= 10 || this == Math.floor(this)) Math.round(this).toString() else "%.1f".format(this)
