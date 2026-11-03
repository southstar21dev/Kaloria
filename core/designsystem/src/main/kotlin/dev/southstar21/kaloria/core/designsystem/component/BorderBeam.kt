// Ported from Jakub Antalik's Border Beam (MIT, https://github.com/Jakubantalik/Libraries.dev,
// packages/border-beam); see liquid/NOTICE.md. The shader is the React Native port's rotate-family
// SkSL in AGSL, extended to draw into a canvas larger than the element (for the bloom's spill);
// palettes and tunings are the library's "md" size, "colorful" variant.
package dev.southstar21.kaloria.core.designsystem.component

import android.graphics.RuntimeShader
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Border Beam: a rainbow glow that rides an element's border. A bright highlight runs around the
 * edge every couple of seconds over a soft inner glow and a blurred bloom, with the hues slowly
 * drifting. Marks what the on-device AI is doing. [active] fades it in and out; it costs nothing
 * once faded out. [dark] picks the tuning for dark surfaces (the camera) or light ones.
 */
@Composable
fun Modifier.borderBeam(
    cornerRadius: Dp,
    active: Boolean = true,
    dark: Boolean = isDarkScheme(),
    strength: Float = 1f,
): Modifier {
    val fade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) FadeInMs else FadeOutMs, easing = FastOutSlowInEasing),
        label = "beam",
    )
    val time = remember { mutableFloatStateOf(0f) }
    if (fade > 0f) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
        }
    }
    val tuning = if (dark) Tuning.Dark else Tuning.Light
    return drawWithCache {
        val inner = RuntimeShader(BeamShader)
        val stroke = RuntimeShader(BeamShader)
        val bloom = RuntimeShader(BeamShader)
        // A pill's radius is half its height; more would break the rounded-rect distance field.
        val radius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
        val border = 1.dp.toPx()
        val px = density
        val spill = (BloomBlurDp * 3).dp.toPx()
        val bloomLayer = obtainGraphicsLayer().apply { renderEffect = BlurEffect(BloomBlurDp.dp.toPx(), BloomBlurDp.dp.toPx(), TileMode.Decal) }
        fun RuntimeShader.common(canvasW: Float, canvasH: Float) {
            setFloatUniform("uSize", canvasW, canvasH)
            setFloatUniform("uRectSize", size.width, size.height)
            setFloatUniform("uRadius", radius)
            setFloatUniform("uBorderWidth", border)
        }
        // Layers that don't change frame to frame.
        inner.common(size.width, size.height)
        inner.setFloatUniform("uKind", 1f)
        inner.setFloatUniform("uEdgeMaskPx", InnerEdgeMaskDp * px)
        inner.setFloatUniform("uBlobs", blobs(px, sizeScale = 0.9f, alpha = 0.45f))
        inner.setFloatUniform("uBlobCount", Palette.size.toFloat())
        inner.setFloatUniform("uBg", FloatArray(MaxBgStops * 2))
        inner.setFloatUniform("uBgCount", 0f)
        inner.setFloatUniform("uBgIsBlack", 0f)
        inner.setFloatUniform("uMask", BeamMask)
        inner.setFloatUniform("uMaskCount", BeamMaskCount)
        inner.setFloatUniform("uShadowColor", tuning.shadow[0], tuning.shadow[1], tuning.shadow[2], tuning.shadow[3])
        inner.setFloatUniform("uShadowBlur", InnerShadowBlurDp * px)

        stroke.common(size.width, size.height)
        stroke.setFloatUniform("uKind", 0f)
        stroke.setFloatUniform("uEdgeMaskPx", 0f)
        stroke.setFloatUniform("uBlobs", blobs(px, sizeScale = 1f, alpha = null))
        stroke.setFloatUniform("uBlobCount", Palette.size.toFloat())
        stroke.setFloatUniform("uBg", tuning.white)
        stroke.setFloatUniform("uBgCount", tuning.whiteCount)
        stroke.setFloatUniform("uBgIsBlack", if (dark) 0f else 1f)
        stroke.setFloatUniform("uMask", BeamMask)
        stroke.setFloatUniform("uMaskCount", BeamMaskCount)
        stroke.setFloatUniform("uShadowColor", 0f, 0f, 0f, 0f)
        stroke.setFloatUniform("uShadowBlur", 0f)

        val bloomW = size.width + spill * 2
        val bloomH = size.height + spill * 2
        bloom.common(bloomW, bloomH)
        bloom.setFloatUniform("uKind", 2f)
        bloom.setFloatUniform("uEdgeMaskPx", 0f)
        bloom.setFloatUniform("uBlobs", FloatArray(MaxBlobs * 8))
        bloom.setFloatUniform("uBlobCount", 0f)
        bloom.setFloatUniform("uBg", tuning.bloom)
        bloom.setFloatUniform("uBgCount", tuning.bloomCount)
        bloom.setFloatUniform("uBgIsBlack", if (dark) 0f else 1f)
        bloom.setFloatUniform("uMask", FloatArray(MaxMaskStops * 2))
        bloom.setFloatUniform("uMaskCount", 0f)
        bloom.setFloatUniform("uShadowColor", 0f, 0f, 0f, 0f)
        bloom.setFloatUniform("uShadowBlur", 0f)
        bloom.setFloatUniform("uCM", filterMatrix(0f, Brightness, tuning.saturation))

        val innerBrush = ShaderBrush(inner)
        val strokeBrush = ShaderBrush(stroke)
        val bloomBrush = ShaderBrush(bloom)
        onDrawWithContent {
            drawContent()
            val f = fade
            if (f <= 0.001f) return@onDrawWithContent
            val t = time.floatValue
            val angle = (t / DurationSeconds) % 1f
            // The colours sway ±30° of hue over 12 s, as on the web.
            val hue = -HueRange + 2 * HueRange * pingPong(t / HueShiftPeriod)
            val matrix = filterMatrix(hue, Brightness, tuning.saturation)
            val s = f * strength
            inner.setFloatUniform("uAngle", angle)
            inner.setFloatUniform("uCM", matrix)
            inner.setFloatUniform("uOpacity", s * tuning.innerOpacity)
            stroke.setFloatUniform("uAngle", angle)
            stroke.setFloatUniform("uCM", matrix)
            stroke.setFloatUniform("uOpacity", s * tuning.strokeOpacity)
            bloom.setFloatUniform("uAngle", angle)
            bloom.setFloatUniform("uOpacity", s * tuning.bloomOpacity)
            drawRect(innerBrush)
            drawRect(strokeBrush)
            // The bloom spills past the edge, so it's drawn into a larger, blurred layer.
            bloomLayer.topLeft = IntOffset(-spill.roundToInt(), -spill.roundToInt())
            bloomLayer.record(IntSize(bloomW.roundToInt(), bloomH.roundToInt())) { drawRect(bloomBrush) }
            drawLayer(bloomLayer)
        }
    }
}

private const val FadeInMs = 600
private const val FadeOutMs = 500
private const val DurationSeconds = 1.96f
private const val HueRange = 30f
private const val HueShiftPeriod = 12f
private const val Brightness = 1.3f
private const val BloomBlurDp = 8f
private const val InnerEdgeMaskDp = 28f
private const val InnerShadowBlurDp = 9f

internal const val MaxBlobs = 12
internal const val MaxBgStops = 16
internal const val MaxMaskStops = 12

/** A palette blob: colour, centre as fractions of the element, radii in dp. */
private class Blob(val r: Int, val g: Int, val b: Int, val x: Float, val y: Float, val rx: Float, val ry: Float)

/** The "colorful" border palette. */
private val Palette = listOf(
    Blob(255, 50, 100, 0.33f, -0.074f, 70f, 40f),
    Blob(40, 140, 255, 0.12f, -0.05f, 60f, 35f),
    Blob(50, 200, 80, 0.021f, 0.683f, 40f, 70f),
    Blob(30, 185, 170, 0.021f, 0.683f, 20f, 35f),
    Blob(100, 70, 255, 0.744f, 1f, 180f, 32f),
    Blob(40, 140, 255, 0.55f, 1f, 85f, 26f),
    Blob(255, 120, 40, 0.939f, 0f, 74f, 32f),
    Blob(240, 50, 180, 1f, 0.271f, 26f, 42f),
    Blob(180, 40, 240, 1f, 0.271f, 52f, 48f),
)

/** Blob uniforms: [rx, ry, cxFrac, cyFrac, r, g, b, a] each, padded to [MaxBlobs]. */
private fun blobs(density: Float, sizeScale: Float, alpha: Float?): FloatArray {
    val out = FloatArray(MaxBlobs * 8)
    Palette.forEachIndexed { i, b ->
        val o = i * 8
        out[o] = (b.rx * sizeScale).roundToInt() * density
        out[o + 1] = (b.ry * sizeScale).roundToInt() * density
        out[o + 2] = b.x
        out[o + 3] = b.y
        out[o + 4] = b.r / 255f
        out[o + 5] = b.g / 255f
        out[o + 6] = b.b / 255f
        out[o + 7] = alpha ?: 1f
    }
    return out
}

/** Stops as [position 0..1, alpha] pairs, padded to [length] pairs. */
private fun stops(length: Int, vararg pairs: Pair<Float, Float>): FloatArray {
    val out = FloatArray(length * 2)
    pairs.forEachIndexed { i, (pos, a) ->
        out[i * 2] = pos / 100f
        out[i * 2 + 1] = a
    }
    return out
}

/** The traveling window: where along the border the beam is lit. */
private val BeamMask = stops(
    MaxMaskStops,
    0f to 0f, 30f to 0f, 36f to 0.1f, 44f to 0.35f, 52f to 1f, 80f to 1f, 86f to 0.35f, 92f to 0.1f, 95f to 0f, 100f to 0f,
)
private const val BeamMaskCount = 20f

private class Tuning(
    val strokeOpacity: Float,
    val innerOpacity: Float,
    val bloomOpacity: Float,
    val shadow: FloatArray,
    val saturation: Float,
    val white: FloatArray,
    val whiteCount: Float,
    val bloom: FloatArray,
    val bloomCount: Float,
) {
    companion object {
        val Dark = Tuning(
            strokeOpacity = 0.26f, innerOpacity = 0.42f, bloomOpacity = 0.24f,
            shadow = floatArrayOf(1f, 1f, 1f, 0.27f), saturation = 1.2f,
            white = stops(MaxBgStops, 0f to 0f, 54f to 0f, 57f to 0.1f, 60f to 0.3f, 63f to 0.6f, 66f to 0.75f, 69f to 0.6f, 72f to 0.3f, 75f to 0.1f, 78f to 0f, 100f to 0f),
            whiteCount = 22f,
            bloom = stops(MaxBgStops, 0f to 0f, 58f to 0f, 62f to 0.03f, 65f to 0.08f, 67f to 0.2f, 69f to 0.45f, 70f to 0.85f, 70.5f to 0.85f, 71.5f to 0.45f, 73f to 0.2f, 75f to 0.08f, 78f to 0.03f, 82f to 0f),
            bloomCount = 26f,
        )
        val Light = Tuning(
            strokeOpacity = 0.12f, innerOpacity = 0.26f, bloomOpacity = 0.34f,
            shadow = floatArrayOf(0f, 0f, 0f, 0.14f), saturation = 1.5f,
            white = stops(MaxBgStops, 0f to 0f, 54f to 0f, 57f to 0.08f, 60f to 0.2f, 63f to 0.4f, 66f to 0.55f, 69f to 0.4f, 72f to 0.2f, 75f to 0.08f, 78f to 0f, 100f to 0f),
            whiteCount = 22f,
            bloom = stops(MaxBgStops, 0f to 0f, 58f to 0f, 62f to 0.02f, 65f to 0.08f, 67f to 0.2f, 69f to 0.4f, 70f to 0.6f, 70.5f to 0.6f, 71.5f to 0.4f, 73f to 0.2f, 75f to 0.08f, 78f to 0.02f, 82f to 0f),
            bloomCount = 26f,
        )
    }
}

/** Cosine ease-in-out in [0, 1]: 0 at phase 0 and 1, 1 at phase 0.5. */
private fun pingPong(phase: Float) = (1 - cos(2 * PI.toFloat() * phase)) / 2

/** CSS `hue-rotate(hue) brightness(b) saturate(s)` as one 3x3 matrix (W3C feColorMatrix math). */
internal fun filterMatrix(hueDegrees: Float, brightness: Float, saturation: Float): FloatArray {
    val rad = hueDegrees * PI.toFloat() / 180f
    val c = cos(rad)
    val s = sin(rad)
    val b = brightness
    val hue = floatArrayOf(
        (0.213f + c * 0.787f - s * 0.213f) * b, (0.715f - c * 0.715f - s * 0.715f) * b, (0.072f - c * 0.072f + s * 0.928f) * b,
        (0.213f - c * 0.213f + s * 0.143f) * b, (0.715f + c * 0.285f + s * 0.140f) * b, (0.072f - c * 0.072f - s * 0.283f) * b,
        (0.213f - c * 0.213f - s * 0.787f) * b, (0.715f - c * 0.715f + s * 0.715f) * b, (0.072f + c * 0.928f + s * 0.072f) * b,
    )
    val k = saturation
    val sat = floatArrayOf(
        0.213f + 0.787f * k, 0.715f - 0.715f * k, 0.072f - 0.072f * k,
        0.213f - 0.213f * k, 0.715f + 0.285f * k, 0.072f - 0.072f * k,
        0.213f - 0.213f * k, 0.715f - 0.715f * k, 0.072f + 0.928f * k,
    )
    val out = FloatArray(9)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            var sum = 0f
            for (i in 0 until 3) sum += sat[row * 3 + i] * hue[i * 3 + col]
            out[row * 3 + col] = sum
        }
    }
    return out
}
