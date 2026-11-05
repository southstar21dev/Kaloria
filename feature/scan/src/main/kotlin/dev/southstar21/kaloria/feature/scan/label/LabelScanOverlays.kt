package dev.southstar21.kaloria.feature.scan.label

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.borderBeam
import dev.southstar21.kaloria.core.scanner.label.LiveLabel
import dev.southstar21.kaloria.core.scanner.label.Pt
import dev.southstar21.kaloria.feature.scan.R
import dev.southstar21.kaloria.feature.scan.camera.ScanAccent

/**
 * The table found in the camera's view, flattened as it will be read: updated live as the phone
 * moves, so people can see it's straight and legible before taking the photo.
 */
@Composable
internal fun LiveFlatCard(flat: Bitmap?) {
    GlassCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (flat != null) {
                Image(
                    bitmap = remember(flat) { flat.asImageBitmap() },
                    contentDescription = stringResource(R.string.label_scan_flattened_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .sizeIn(maxWidth = 190.dp, maxHeight = 120.dp)
                        .aspectRatio(flat.width.toFloat() / flat.height)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.label_scan_table_found), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.label_scan_table_found_body), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * The table's outline over the camera, live: the view outside it dimmed, and a grid on its surface
 * that bends with it (round a can, the lines curve), so it's clear what's been found and how it
 * will be straightened. Each new outline glides in from the last rather than jumping.
 */
@Composable
internal fun LiveOutline(live: LiveLabel?, accent: Color = ScanAccent) {
    var start by remember { mutableStateOf(emptyList<Pt>()) }
    var target by remember { mutableStateOf(emptyList<Pt>()) }
    var outlineSize by remember { mutableIntStateOf(0) }
    var gridLines by remember { mutableStateOf(emptyList<Int>()) }
    val glide = remember { Animatable(1f) }
    val shown by animateFloatAsState(if (live != null) 1f else 0f, tween(if (live != null) 200 else 320), label = "outline")
    LaunchedEffect(live) {
        live ?: return@LaunchedEffect
        val next = live.outline + live.grid.flatten()
        val now = if (start.size == target.size) blend(start, target, glide.value) else target
        start = if (now.size == next.size) now else next
        target = next
        outlineSize = live.outline.size
        gridLines = live.grid.map { it.size }
        glide.snapTo(0f)
        glide.animateTo(1f, tween(220, easing = EmphasizedEasing))
    }
    // Reused every frame rather than allocated per draw.
    val outline = remember { Path() }
    val line = remember { Path() }
    val density = LocalDensity.current
    val gridStroke = remember(density) { Stroke(with(density) { 1.dp.toPx() }) }
    val glowStroke = remember(density) { Stroke(with(density) { 9.dp.toPx() }, join = StrokeJoin.Round) }
    val edgeStroke = remember(density) { Stroke(with(density) { 3.dp.toPx() }, join = StrokeJoin.Round) }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = shown
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        if (target.isEmpty() || shown == 0f) return@Canvas
        val points = blend(start, target, glide.value).map { Offset(it.x * size.width, it.y * size.height) }
        outline.polyline(points, 0, outlineSize)
        outline.close()
        drawRect(Color.Black.copy(alpha = 0.42f))
        drawPath(outline, Color.Black, blendMode = BlendMode.Clear)
        var at = outlineSize
        for (count in gridLines) {
            line.polyline(points, at, at + count)
            drawPath(line, Color.White.copy(alpha = 0.3f), style = gridStroke)
            at += count
        }
        drawPath(outline, accent.copy(alpha = 0.3f), style = glowStroke)
        drawPath(outline, accent, style = edgeStroke)
    }
}

/** Replaces the path with a line through [points] from index [from] until [until]. */
private fun Path.polyline(points: List<Offset>, from: Int, until: Int) {
    rewind()
    moveTo(points[from].x, points[from].y)
    for (i in from + 1 until until) lineTo(points[i].x, points[i].y)
}

/** Material's emphasized easing, for a glide that settles firmly. */
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun blend(a: List<Pt>, b: List<Pt>, t: Float): List<Pt> = if (a.size != b.size) b else a.indices.map { i -> a[i] + (b[i] - a[i]) * t }

/**
 * The table as it's being read: cropped and flattened from the photo, it settles into the frame
 * while the beam runs round it, so it's clear what the AI is looking at.
 */
@Composable
internal fun FlatTablePreview(table: Bitmap?, modifier: Modifier = Modifier) {
    // Keeps showing the last table while it fades out.
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    SideEffect { if (table != null) shown = table }
    AnimatedVisibility(
        visible = table != null,
        modifier = modifier,
        enter = fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.9f),
        exit = fadeOut(tween(180)),
    ) {
        val image = table ?: shown ?: return@AnimatedVisibility
        val shape = RoundedCornerShape(20.dp)
        Image(
            bitmap = remember(image) { image.asImageBitmap() },
            contentDescription = stringResource(R.string.label_scan_flattened_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .sizeIn(maxWidth = 300.dp, maxHeight = 420.dp)
                .aspectRatio(image.width.toFloat() / image.height)
                .borderBeam(cornerRadius = 20.dp, dark = true)
                .clip(shape)
                .background(Color.White),
        )
    }
}
