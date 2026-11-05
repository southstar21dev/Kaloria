// Tab bar construction adapted from Kyant0/AndroidLiquidGlass's LiquidBottomTabs (Apache-2.0); see NOTICE.md.
package dev.southstar21.kaloria.core.designsystem.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import dev.southstar21.kaloria.core.designsystem.component.LocalFloatingBackdrop
import dev.southstar21.kaloria.core.designsystem.component.LocalReduceTransparency
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * iOS 26-style Liquid Glass tabs: one pane of glass whose selection is itself a drop of glass you
 * can tap, or press and drag between items. The drop magnifies the item beneath it (shown in the
 * accent colour) with a little chromatic aberration, swells while held and squashes with speed.
 * Used by the dock and by the Today week strip.
 *
 * [onSelect] fires once the drop has nearly arrived, so whatever the selection triggers doesn't
 * make the drop stutter on its way there.
 *
 * @param backdrop what the glass refracts: the floating backdrop for bars over content, the
 *   plain field for tabs that scroll with the content (which mustn't refract themselves).
 * @param floating a drop shadow under the pane, for tabs floating above content.
 * @param item one item's content; [tinted] is the accent copy the drop shows.
 */
@Composable
fun LiquidTabs(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    paneShape: Shape = Capsule(),
    backdrop: Backdrop = LocalFloatingBackdrop.current,
    floating: Boolean = true,
    item: @Composable ColumnScope.(index: Int, tinted: Boolean) -> Unit,
) {
    val selectedNow by rememberUpdatedState(selectedIndex)
    val onSelectNow by rememberUpdatedState(onSelect)
    val reduce = LocalReduceTransparency.current
    val accent = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.surface.copy(alpha = if (reduce) 0.94f else 0.30f)
    val shadowColor = Color.Black.copy(alpha = 0.10f)
    val itemsBackdrop = rememberLayerBackdrop()
    val pillHeight = height - 8.dp

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val itemWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / count }
        // Read inside long-lived lambdas, so a resize reaches them without rebuilding the drag.
        val itemWidthNow by rememberUpdatedState(itemWidth)
        val maxWidthNow by rememberUpdatedState(constraints.maxWidth.toFloat())
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / maxWidthNow).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val scope = rememberCoroutineScope()
        // One state for the tabs' lifetime: taps and drags write it, the caller follows it.
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        val drag = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    currentIndex = target
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, amount ->
                    updateValue((targetValue + amount.x / itemWidthNow).fastCoerceIn(0f, (count - 1).toFloat()))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                },
            )
        }
        // Follow selection changes that didn't come from the tabs (back, swipes, shortcuts).
        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) {
                currentIndex = selectedIndex
                drag.animateToValue(selectedIndex.toFloat())
            }
        }
        LaunchedEffect(drag) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                drag.animateToValue(index.toFloat())
                snapshotFlow { abs(drag.value - index) < 0.2f }.first { it }
                if (index != selectedNow) onSelectNow(index)
            }
        }

        val highlight = remember(scope, drag) {
            InteractiveHighlight(scope, position = { size, _ -> Offset((drag.value + 0.5f) * itemWidthNow + panelOffset, size.height / 2f) })
        }

        @Composable
        fun items(tinted: Boolean, rowModifier: Modifier, scale: () -> Float = { 1f }) = Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            repeat(count) { index ->
                Column(
                    Modifier
                        .clip(Capsule())
                        .selectable(selected = index == currentIndex, interactionSource = null, indication = null, role = Role.Tab) { currentIndex = index }
                        .fillMaxHeight()
                        .weight(1f)
                        .graphicsLayer {
                            val s = scale()
                            scaleX = s
                            scaleY = s
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { item(index, tinted) }
            }
        }

        // The pane: one piece of glass holding the items.
        items(
            tinted = false,
            rowModifier = Modifier
                .selectableGroup()
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { paneShape },
                    effects = {
                        if (reduce) {
                            blur(32.dp.toPx())
                        } else {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(24.dp.toPx(), 34.dp.toPx())
                        }
                    },
                    layerBlock = {
                        val s = lerp(1f, 1f + 16.dp.toPx() / size.width, drag.pressProgress)
                        scaleX = s
                        scaleY = s
                    },
                    shadow = if (floating) ({ Shadow(radius = 24.dp, color = shadowColor) }) else null,
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(height)
                .fillMaxWidth()
                .padding(4.dp),
        )

        // An invisible accent-tinted copy of the items, recorded so the drop shows the item under
        // it in the accent colour as it passes. Its own glass only blurs while the drop is held.
        items(
            tinted = true,
            scale = { lerp(1f, 1.2f, drag.pressProgress) },
            rowModifier = Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(itemsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { paneShape },
                    effects = {
                        val p = drag.pressProgress
                        vibrancy()
                        if (p > 0f) {
                            blur(8.dp.toPx() * p)
                            lens(24.dp.toPx() * p, 24.dp.toPx() * p)
                        }
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(pillHeight)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
        )

        // The selection: a drop of glass, always lensing the accent copy under it, that swells and
        // magnifies more while held.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer { translationX = drag.value * itemWidth + panelOffset }
                .then(highlight.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, itemsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val p = lerp(0.6f, 1f, drag.pressProgress)
                        lens(10.dp.toPx() * p, 14.dp.toPx() * p, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = lerp(0.8f, 1f, drag.pressProgress)) },
                    shadow = { Shadow(radius = 8.dp, alpha = lerp(0.35f, 1f, drag.pressProgress)) },
                    innerShadow = { InnerShadow(radius = 3.dp + 5.dp * drag.pressProgress, alpha = lerp(0.5f, 1f, drag.pressProgress)) },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val v = drag.velocity / 10f
                        scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val p = drag.pressProgress
                        drawRect(accent.copy(alpha = 0.14f), alpha = 1f - p)
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    },
                )
                .height(pillHeight)
                .fillMaxWidth(1f / count),
        )
    }
}

/**
 * Keeps sideways drags inside this element from also reaching a parent's swipe gesture (Today
 * swipes between days), while the tabs inside still see them first. A gesture is only taken once
 * it has moved past the touch slop mostly sideways; vertical ones are left for a parent's scroll.
 */
fun Modifier.consumeHorizontalDrags(): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        var travel = Offset.Zero
        var decided = false
        var horizontal = false
        do {
            val event = awaitPointerEvent()
            if (!decided) {
                event.changes.forEach { travel += it.positionChangeIgnoreConsumed() }
                if (travel.getDistance() > slop) {
                    decided = true
                    horizontal = abs(travel.x) > abs(travel.y)
                }
            }
            if (horizontal) event.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
