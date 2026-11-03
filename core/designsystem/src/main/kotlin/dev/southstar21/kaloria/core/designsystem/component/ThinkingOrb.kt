// Ported from Jakub Antalik's thinking-orbs (MIT, https://github.com/Jakubantalik/thinking-orbs);
// see liquid/NOTICE.md. Geometry and tunings follow the original's engine and 64 px presets.
package dev.southstar21.kaloria.core.designsystem.component

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What the orb is doing. Each is its own dotted, depth-shaded 3D motion. */
enum class OrbState(@StringRes internal val label: Int) {
    /** A scan meridian sweeps a dotted globe. */
    Searching(R.string.thinking_orb_searching),

    /** Particles run on tilted orbits. */
    Working(R.string.thinking_orb_working),
}

/**
 * A "thinking" indicator: a monochrome dotted 3D form, rotated, depth-shaded and drawn back to front,
 * where nearer dots are bigger and stronger in ink. [dark] mirrors the ink for dark backgrounds
 * (the camera). The clock is read only while drawing, so it animates without recomposing.
 *
 * With animations turned off in the system settings, or [animate] false, it holds one still
 * frame, as the original does for reduced motion.
 */
@Composable
fun ThinkingOrb(state: OrbState, modifier: Modifier = Modifier, size: Dp = 64.dp, dark: Boolean = false, animate: Boolean = true) {
    val context = LocalContext.current
    val moving = animate && remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val time = remember { mutableFloatStateOf(StillFrame) }
    if (moving) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { time.floatValue = StillFrame + (it - start) / 1_000_000_000f }
        }
    }
    val dots = remember { DotBuffer() }
    // The geometry works in the original's CSS-px units, one per dp here.
    val units = size.value
    val description = stringResource(state.label)
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val scale = this.size.minDimension / units
        val t = time.floatValue
        dots.clear()
        when (state) {
            OrbState.Searching -> globe(dots, units, t * GlobeSpeed)
            OrbState.Working -> orbits(dots, units, t * OrbitsSpeed)
        }
        dots.sortByDepth()
        for (n in 0 until dots.count) {
            val i = dots.order[n]
            val ink = dots.white[i].coerceIn(0f, 1f).let { if (dark) 1f - it else it }
            drawCircle(
                Color(ink, ink, ink, dots.alpha[i].coerceIn(0f, 1f)),
                radius = max(MinRadius, dots.r[i]) * scale,
                center = Offset(dots.x[i] * scale, dots.y[i] * scale),
            )
        }
    }
}

/** Reused per-frame dot storage, so a running orb allocates nothing. */
private class DotBuffer {
    var count = 0
        private set
    var x = FloatArray(512)
    var y = FloatArray(512)
    var z = FloatArray(512)
    var r = FloatArray(512)
    var white = FloatArray(512)
    var alpha = FloatArray(512)

    /** Draw order after [sortByDepth]: indices from farthest to nearest. */
    var order = IntArray(512)
        private set

    fun clear() {
        count = 0
    }

    fun add(px: Float, py: Float, pz: Float, radius: Float, ink: Float, a: Float) {
        if (a < 0.02f) return
        if (count == x.size) grow()
        x[count] = px
        y[count] = py
        z[count] = pz
        r[count] = radius
        white[count] = ink
        alpha[count] = a
        count++
    }

    private fun grow() {
        val n = x.size * 2
        x = x.copyOf(n)
        y = y.copyOf(n)
        z = z.copyOf(n)
        r = r.copyOf(n)
        white = white.copyOf(n)
        alpha = alpha.copyOf(n)
        order = IntArray(n)
    }

    /** Far to near, so nearer dots paint over farther ones. Insertion sort: frames are nearly sorted already. */
    fun sortByDepth() {
        for (i in 0 until count) order[i] = i
        for (i in 1 until count) {
            val k = order[i]
            var j = i - 1
            while (j >= 0 && z[order[j]] > z[k]) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = k
        }
    }
}

/** Spin + tilt + orthographic projection, as in the original's `makeProj`. */
private class Projector(yaw: Float, tilt: Float, private val cx: Float, private val cy: Float, private val scale: Float) {
    private val st = sin(tilt)
    private val ct = cos(tilt)
    private val sy = sin(yaw)
    private val cyw = cos(yaw)
    var px = 0f
    var py = 0f
    var pz = 0f

    fun project(x: Float, y: Float, z: Float) {
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        pz = y * st + z1 * ct
        px = cx + x1 * scale
        py = cy - y1 * scale
    }
}

/** Dot radii were tuned for a 300-unit frame; sub-linear scaling keeps small orbs legible. */
private fun radiusScale(size: Float) = (size / 300f).pow(0.6f)

private fun hashD(a: Float, b: Float): Float {
    val h = sin(a * 12.9898f + b * 78.233f) * 43758.5453f
    return h - floor(h)
}

private fun angleDelta(a: Float, b: Float) = atan2(sin(a - b), cos(a - b))

// --- Searching: a lat/long dot globe; a meridian sweeps it as a size ripple ---------------------

// The 64 px preset: count 0.42 (√ per lattice side), radii 1.15, scanMul 4.08, dimBase 0.45.
private const val GlobeSpeed = 2.015f
private val GlobeLatRings = (17 * sqrt(0.42f)).roundToInt()
private val GlobeLonDensity = (44 * sqrt(0.42f)).roundToInt()

private fun globe(dots: DotBuffer, size: Float, t: Float) {
    val spin = 0.5f
    val radius = size / 2 * 0.82f
    val pt = Projector(t * spin, 0.4f + 0.06f * sin(t * 0.35f), size / 2, size / 2, radius)
    val scan = t * (spin + (1.7f - spin) * 4.08f)
    val rs = radiusScale(size)
    val dimBase = 0.45f
    for (li in 0..GlobeLatRings) {
        val lat = (-PI / 2 + li.toDouble() / GlobeLatRings * PI).toFloat()
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1, (abs(cosLat) * GlobeLonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toFloat() / lonCount) * 2 * PI.toFloat()
            pt.project(cosLat * cos(lon), sinLat, cosLat * sin(lon))
            val depth = (pt.pz + 1) / 2
            val d = angleDelta(lon + t * spin, scan)
            val boost = exp(-(d * d) / 0.18f) * max(0f, pt.pz)
            dots.add(
                pt.px,
                pt.py,
                pt.pz,
                radius = (0.6f * 1.15f + 1.7f * 1.15f * depth + 1.0f * boost) * rs,
                ink = 0.62f - 0.54f * depth,
                a = dimBase + (1 - dimBase) * min(1f, boost),
            )
        }
    }
}

// --- Working: particles on tilted orbits, over faint ghost paths --------------------------------

private const val OrbitsSpeed = 1.885f
private const val OrbitN = 12
private const val GhostN = 40
private const val Particles = 3

private fun orbits(dots: DotBuffer, size: Float, t: Float) {
    val r = size / 2 * 0.82f
    val pt = Projector(t * 0.12f, 0.3f, size / 2, size / 2, 1f)
    val rs = radiusScale(size)
    for (orb in 0 until OrbitN) {
        val h1 = hashD(orb.toFloat(), 1.7f)
        val h2 = hashD(orb.toFloat(), 5.2f)
        val h3 = hashD(orb.toFloat(), 8.9f)
        val ro = r * (0.45f + 0.52f * h1)
        val th = h1 * 2 * PI.toFloat()
        val phi = acos(2 * h2 - 1)
        // Orbit plane basis (u, v perpendicular to the normal n).
        val nx = sin(phi) * cos(th)
        val ny = cos(phi)
        val nz = sin(phi) * sin(th)
        var ux = -ny
        var uy = nx
        val uz = 0f
        val ul = max(1e-6f, sqrt(ux * ux + uy * uy))
        ux /= ul
        uy /= ul
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val speed = (0.25f + 0.55f * h3) * (if (h3 > 0.5f) 1 else -1)
        for (k in 0 until GhostN) {
            val a = (k.toFloat() / GhostN) * 2 * PI.toFloat()
            pt.project((ux * cos(a) + vx * sin(a)) * ro, (uy * cos(a) + vy * sin(a)) * ro, (uz * cos(a) + vz * sin(a)) * ro)
            val depth = (pt.pz / ro + 1) / 2
            dots.add(pt.px, pt.py, pt.pz, 0.9f * rs, 0.72f, 0.5f * (0.4f + 0.6f * depth))
        }
        for (m in 0 until Particles) {
            val a = t * speed + (m.toFloat() / Particles) * 2 * PI.toFloat() + h2 * 6
            pt.project((ux * cos(a) + vx * sin(a)) * ro, (uy * cos(a) + vy * sin(a)) * ro, (uz * cos(a) + vz * sin(a)) * ro)
            val depth = (pt.pz / ro + 1) / 2
            dots.add(pt.px, pt.py, pt.pz, (1.2f + 1.6f * depth) * rs, 0.3f - 0.22f * depth, 1f)
        }
    }
}

private const val MinRadius = 0.3f

/** The still frame shown without motion, in seconds of the orb's clock (the original's choice). */
private const val StillFrame = 0.6f
