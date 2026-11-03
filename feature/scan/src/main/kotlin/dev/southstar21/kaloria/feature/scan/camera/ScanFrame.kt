package dev.southstar21.kaloria.feature.scan.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.scanner.BarcodeSighting
import kotlin.math.max
import kotlin.math.min

/**
 * The scanning frame over the camera: the view outside it dimmed, four rounded corner brackets at
 * its corners. Given a [target] (in this composable's pixels) it springs onto it and follows it;
 * without one it rests at [restSize] in the middle, breathing gently. [locked] turns the brackets
 * the accent colour and closes them into a full outline, for "got it".
 */
@Composable
internal fun ScanFrame(
    target: Rect?,
    restSize: DpSize,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    locked: Boolean = false,
    accent: Color = Color(0xFFA9C8EA),
) {
    val density = LocalDensity.current
    var area by remember { mutableStateOf(IntSize.Zero) }
    val rest = remember(area, restSize) {
        with(density) {
            val w = restSize.width.toPx()
            val h = restSize.height.toPx()
            Rect(Offset((area.width - w) / 2f, (area.height - h) / 2f), Size(w, h))
        }
    }
    val padding = with(density) { 16.dp.toPx() }
    val goal = target?.let { Rect(it.left - padding, it.top - padding, it.right + padding, it.bottom + padding) } ?: rest
    val frame = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placedFor by remember { mutableStateOf(IntSize.Zero) }
    // The size this goal was worked out for, read here with it: reading the state inside the
    // effect could see a newer size than the goal was computed from, and snap to a stale spot.
    val goalArea = area
    LaunchedEffect(goal, goalArea) {
        if (goalArea == IntSize.Zero) return@LaunchedEffect
        // A new size (the first layout, or the screen settling as it opens) moves the resting place
        // with it: snap there rather than glide, or the frame drifts diagonally while the screen
        // slides in. It glides only to follow a code, or back to rest once the code is gone.
        if (goalArea != placedFor || frame.value == Rect.Zero) {
            placedFor = goalArea
            frame.snapTo(goal)
        } else {
            frame.animateTo(goal, spring(dampingRatio = if (target != null) 0.78f else 0.85f, stiffness = if (target != null) 420f else 260f))
        }
    }
    val breathing by rememberInfiniteTransition(label = "frame").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val idle by animateFloatAsState(if (target == null && !locked) 1f else 0f, tween(300), label = "idle")
    val close by animateFloatAsState(if (locked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 380f), label = "lock")
    val color by animateColorAsState(if (locked || target != null) accent else Color.White, tween(220), label = "color")
    val radius = with(density) { cornerRadius.toPx() }
    val stroke = with(density) { 4.dp.toPx() }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { area = it }
            // Offscreen, so the cut-out clears only the dimming, not the camera beneath.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        var r = frame.value
        if (r == Rect.Zero) return@Canvas
        val grow = idle * breathing * 6.dp.toPx()
        r = Rect(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow)
        val corner = min(radius, min(r.width, r.height) / 3f)
        drawRect(Color.Black.copy(alpha = 0.42f))
        drawRoundRect(Color.Black, r.topLeft, r.size, CornerRadius(corner), blendMode = BlendMode.Clear)
        if (close > 0f) {
            drawRoundRect(color.copy(alpha = 0.9f * close), r.topLeft, r.size, CornerRadius(corner), style = Stroke(stroke * 0.6f))
        }
        brackets(r, corner, stroke, color)
    }
}

/** Four L-shaped corners with rounded elbows, each a third of the shorter side long. */
private fun DrawScope.brackets(r: Rect, corner: Float, width: Float, color: Color) {
    val arm = max(corner + 8.dp.toPx(), min(r.width, r.height) * 0.28f)
    val path = Path().apply {
        // Top left
        moveTo(r.left, r.top + arm)
        lineTo(r.left, r.top + corner)
        quadraticTo(r.left, r.top, r.left + corner, r.top)
        lineTo(r.left + arm, r.top)
        // Top right
        moveTo(r.right - arm, r.top)
        lineTo(r.right - corner, r.top)
        quadraticTo(r.right, r.top, r.right, r.top + corner)
        lineTo(r.right, r.top + arm)
        // Bottom right
        moveTo(r.right, r.bottom - arm)
        lineTo(r.right, r.bottom - corner)
        quadraticTo(r.right, r.bottom, r.right - corner, r.bottom)
        lineTo(r.right - arm, r.bottom)
        // Bottom left
        moveTo(r.left + arm, r.bottom)
        lineTo(r.left + corner, r.bottom)
        quadraticTo(r.left, r.bottom, r.left, r.bottom - corner)
        lineTo(r.left, r.bottom - arm)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round))
}

/** A sighting in this view's pixels: the viewport makes the analysed crop exactly what the view shows. */
internal fun BarcodeSighting.toView(view: IntSize): Rect = Rect(left * view.width, top * view.height, right * view.width, bottom * view.height)
