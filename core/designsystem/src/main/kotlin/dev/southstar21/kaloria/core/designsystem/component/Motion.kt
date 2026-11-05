package dev.southstar21.kaloria.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shrinks slightly while pressed and springs back, so taps feel physical. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier = this then PressScaleElement(interactionSource, pressedScale)

private data class PressScaleElement(val interactionSource: InteractionSource, val pressedScale: Float) : ModifierNodeElement<PressScaleNode>() {
    override fun create() = PressScaleNode(interactionSource, pressedScale)

    override fun update(node: PressScaleNode) = node.update(interactionSource, pressedScale)

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["interactionSource"] = interactionSource
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(private var interactionSource: InteractionSource, private var pressedScale: Float) :
    Modifier.Node(),
    LayoutModifierNode {
    private var scale = Animatable(1f)
    private var pressed = false
    private var collecting: Job? = null

    // Read while placing the layer, so the spring redraws it without remeasuring.
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        val s = scale.value
        scaleX = s
        scaleY = s
    }

    override fun onAttach() = collectPresses()

    override fun onDetach() {
        // Reused nodes (lazy lists) start unpressed rather than mid-spring.
        pressed = false
        collecting = null
        scale = Animatable(1f)
    }

    fun update(source: InteractionSource, scaleWhilePressed: Float) {
        if (scaleWhilePressed != pressedScale) {
            pressedScale = scaleWhilePressed
            if (pressed) animate()
        }
        if (source != interactionSource) {
            interactionSource = source
            collecting?.cancel()
            if (pressed) {
                pressed = false
                animate()
            }
            collectPresses()
        }
    }

    private fun collectPresses() {
        collecting = coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val now = presses.isNotEmpty()
                if (now != pressed) {
                    pressed = now
                    animate()
                }
            }
        }
    }

    private fun animate() {
        coroutineScope.launch { scale.animateTo(if (pressed) pressedScale else 1f, PressSpring) }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layerBlock) }
    }
}

private val PressSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/**
 * Remembers whether a screen's entrance has already played, so items cascade in when the screen
 * opens but not again when they scroll back into view.
 */
@Stable
class IntroState internal constructor(played: Boolean) {
    var played by mutableStateOf(played)
        internal set
}

@Composable
fun rememberIntro(): IntroState {
    var played by rememberSaveable { mutableStateOf(false) }
    val state = remember { IntroState(played) }
    LaunchedEffect(Unit) {
        delay(900)
        played = true
        state.played = true
    }
    return state
}

/** Fades and lifts the item in, [index] steps after the first, the first time the screen shows. */
@Composable
fun Modifier.introItem(intro: IntroState, index: Int): Modifier {
    // Decided once per item: an item still cascading in when the intro ends keeps animating
    // rather than snapping into place.
    val skip = remember { intro.played }
    if (skip) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(45L * index.coerceAtMost(8))
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    val lift = with(LocalDensity.current) { 28.dp.toPx() }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * lift
        val s = 0.96f + 0.04f * progress.value
        scaleX = s
        scaleY = s
    }
}

/** A number that counts to its new value instead of jumping. Digits are tabular so it doesn't wobble. */
@Composable
fun AnimatedCount(
    value: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    format: (Int) -> String = { it.grouped() },
) {
    val shown by animateIntAsState(value, tween(durationMillis = 650), label = "count")
    Text(format(shown), style = style.copy(fontFeatureSettings = "tnum"), color = color, modifier = modifier)
}
