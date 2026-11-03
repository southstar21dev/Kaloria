package dev.southstar21.kaloria.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shrinks slightly while pressed and springs back, so taps feel physical. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

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
    if (intro.played) return this
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
