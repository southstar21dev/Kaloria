package dev.southstar21.kaloria.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import dev.southstar21.kaloria.core.designsystem.liquid.InteractiveHighlight
import dev.southstar21.kaloria.core.designsystem.liquid.LocalLightSource
import dev.southstar21.kaloria.core.designsystem.liquid.rememberLightSource
import dev.southstar21.kaloria.core.designsystem.theme.Brand

/*
 * Liquid Glass, after Apple's WWDC25 "Meet Liquid Glass": a material defined by lensing (it bends
 * and concentrates what's behind it instead of only blurring it), a specular rim lit from a light
 * source that follows device tilt, adaptive tint for legibility, and touch feedback that makes it
 * swell and glow from the fingertip. Thicker material for larger surfaces (cards) than for
 * controls. Rendered with Kyant's Backdrop (AGSL refraction, API 33+).
 */

/** What content-level glass (cards) refracts: the colour field behind the app. */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/**
 * What floating glass (bars, buttons, the dock) refracts: the colour field plus the live content
 * of the nearest [GlassScreen], so scrolling cards bend through the bar above them.
 */
val LocalFloatingBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/** The layer an enclosing [GlassScreen] records its content into; [glassSource] adds to it. */
private val LocalGlassSourceLayer = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Glass over a live camera: nothing can be captured behind it, so it's tinted, not refracting. */
val LocalGlassOverCamera = staticCompositionLocalOf { false }

/** When true, glass is frosted and opaque enough to read anything (accessibility setting). */
val LocalReduceTransparency = staticCompositionLocalOf { false }

/**
 * Height the floating navigation dock covers at the bottom of top-level screens, including the
 * navigation bar inset. Screens pad their scrolling content by it.
 */
val LocalDockPadding = compositionLocalOf { 0.dp }

/** How thick the material is. Bigger glass bends more and scatters light more softly. */
enum class GlassStyle(
    internal val blur: Dp,
    internal val refractionHeight: Dp,
    internal val refractionAmount: Dp,
    internal val depth: Boolean,
    internal val surfaceLight: Float,
    internal val surfaceDark: Float,
    internal val shadowRadius: Dp,
) {
    /** Content cards: thick, strongly lensed edges with depth. */
    Card(blur = 3.dp, refractionHeight = 30.dp, refractionAmount = 52.dp, depth = true, surfaceLight = 0.30f, surfaceDark = 0.34f, shadowRadius = 28.dp),

    /** Floating bars and the dock. */
    Bar(blur = 2.dp, refractionHeight = 24.dp, refractionAmount = 34.dp, depth = false, surfaceLight = 0.28f, surfaceDark = 0.34f, shadowRadius = 24.dp),

    /** Small controls: buttons, chips, menu items. */
    Control(blur = 2.dp, refractionHeight = 14.dp, refractionAmount = 28.dp, depth = false, surfaceLight = 0.26f, surfaceDark = 0.32f, shadowRadius = 16.dp),
}

/** Whether the current color scheme is the dark one (the app's theme, not the system setting). */
@Composable
internal fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Root of every screen: paints the colour field the glass bends, records it as the backdrop, and
 * starts the motion-driven light source for specular highlights.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val field = rememberLayerBackdrop()
    val light = rememberLightSource()
    // Glass panels aren't Material Surfaces, so nothing else sets the content color: without this,
    // text and icons fall back to black, which is unreadable in dark theme.
    CompositionLocalProvider(
        LocalGlassBackdrop provides field,
        LocalFloatingBackdrop provides field,
        LocalLightSource provides light,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier.fillMaxSize()) {
            AuroraBackground(Modifier.fillMaxSize().layerBackdrop(field))
            content()
        }
    }
}

/**
 * A screen whose floating glass (bars, buttons) refracts its scrolling content as well as the
 * colour field. The content marks itself with [glassSource]; floating glass must be a sibling of
 * that content, never inside it. Cards inside the content refract only the colour field.
 */
@Composable
fun GlassScreen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(LocalGlassBackdrop.current, layer)
    CompositionLocalProvider(LocalGlassSourceLayer provides layer, LocalFloatingBackdrop provides combined) {
        Box(modifier.fillMaxSize(), content = content)
    }
}

/** Records this content (e.g. a scrolling list) so the enclosing [GlassScreen]'s floating glass refracts it. */
@Composable
fun Modifier.glassSource(): Modifier {
    val layer = LocalGlassSourceLayer.current ?: return this
    return layerBackdrop(layer)
}

/** Composable-scope variant of the app shell's own content capture (for the dock). */
@Composable
fun rememberGlassLayer(): LayerBackdrop = rememberLayerBackdrop()

@Composable
fun FloatingGlassScope(layer: LayerBackdrop, content: @Composable () -> Unit) {
    val combined = rememberCombinedBackdrop(LocalGlassBackdrop.current, layer)
    CompositionLocalProvider(LocalFloatingBackdrop provides combined, content = content)
}

@Composable
private fun AuroraBackground(modifier: Modifier) {
    val background = MaterialTheme.colorScheme.background
    val dark = isDarkScheme()
    val dot = MaterialTheme.colorScheme.onBackground.copy(alpha = if (dark) 0.14f else 0.11f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dots = remember(dot, density) { dotGridBrush(dot, with(density) { 22.dp.toPx() }, with(density) { 1.3.dp.toPx() }) }
    // Ink and coral haze over cream; in the dark, deep ink and an ember glow over night blue.
    // Liquid Glass only reads when there's something behind it to bend, so the field has real colour.
    val glows = if (dark) {
        listOf(Brand.DeepInk.copy(alpha = 0.95f), Brand.Ember.copy(alpha = 0.65f), Brand.Ink.copy(alpha = 0.55f), Brand.DeepInk.copy(alpha = 0.85f))
    } else {
        listOf(Brand.Ink.copy(alpha = 0.42f), Brand.Coral.copy(alpha = 0.45f), Brand.Mist.copy(alpha = 0.85f), Brand.Ink.copy(alpha = 0.30f))
    }
    Box(
        modifier
            .background(background)
            .drawBehind {
                fun blob(color: Color, center: Offset, radius: Float) = drawCircle(
                    brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
                    radius = radius,
                    center = center,
                )
                val w = size.width
                val h = size.height
                blob(glows[0], Offset(w * 0.05f, h * 0.05f), w * 0.9f)
                blob(glows[1], Offset(w * 1.0f, h * 0.32f), w * 0.75f)
                blob(glows[2], Offset(w * 0.1f, h * 0.68f), w * 0.85f)
                blob(glows[3], Offset(w * 0.9f, h * 0.98f), w * 0.7f)
                // A fine dot grid (the southstar21 family's dot motif). Refraction is only visible
                // where there's detail to bend: the lensed edges of every glass element visibly
                // warp these dots, the way iOS glass warps the wallpaper behind it. Drawn as one
                // tiled shader, because every glass element redraws this backdrop each frame.
                drawRect(dots)
            },
    )
}

object GlassDefaults {
    /** Continuous (squircle) corners like iOS, which the lens shader follows exactly. */
    val Shape: Shape = RoundedRectangle(28.dp)
}

/** Maps Material shapes to the continuous-corner shapes the refraction understands. */
private fun Shape.continuous(): Shape = when (this) {
    CircleShape -> Capsule()
    is RoundedCornerShape -> this
    else -> this
}

/**
 * Liquid Glass. [style] sets the thickness; [interaction] (from [rememberGlassInteraction]) makes
 * it swell and glow from the touch point while pressed.
 */
@Composable
fun Modifier.glass(
    shape: Shape = GlassDefaults.Shape,
    style: GlassStyle = GlassStyle.Bar,
    interaction: InteractiveHighlight? = null,
    tint: Color = Color.Unspecified,
): Modifier {
    val backdrop = when {
        LocalGlassOverCamera.current -> emptyBackdrop()
        style == GlassStyle.Card -> LocalGlassBackdrop.current
        else -> LocalFloatingBackdrop.current
    }
    val overCamera = LocalGlassOverCamera.current
    val reduce = LocalReduceTransparency.current
    val dark = isDarkScheme()
    val light = LocalLightSource.current
    val glassShape = shape.continuous()
    val base = MaterialTheme.colorScheme.surface
    // Adaptive tint: enough of the surface colour that text stays legible, more when frosted.
    val surface = when {
        overCamera -> Color.Black.copy(alpha = 0.32f)
        reduce -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        else -> base.copy(alpha = if (dark) style.surfaceDark else style.surfaceLight)
    }
    val rim = Color.White.copy(alpha = if (dark) 0.55f else 0.85f)
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { glassShape },
            effects = {
                if (reduce) {
                    blur(32.dp.toPx())
                } else {
                    if (style != GlassStyle.Card) {
                        vibrancy()
                        blur(style.blur.toPx())
                    }
                    lens(style.refractionHeight.toPx(), style.refractionAmount.toPx(), depthEffect = style.depth)
                }
            },
            highlight = {
                Highlight(
                    width = if (style == GlassStyle.Card) 1.dp else 0.75.dp,
                    alpha = if (reduce) 0.4f else 1f,
                    style = HighlightStyle.Default(color = rim, angle = light.angle),
                )
            },
            shadow = if (style == GlassStyle.Card) null else ({ Shadow(radius = style.shadowRadius, color = shadowColor) }),
            layerBlock = interaction?.let { ih ->
                {
                    // Gel-like: swells a few dp and leans toward the finger while pressed.
                    val p = ih.pressProgress
                    val s = lerp(1f, 1f + 6.dp.toPx() / size.height.coerceAtLeast(1f), p).coerceAtMost(1.04f)
                    scaleX = s; scaleY = s
                    val max = size.minDimension
                    translationX = max * kotlin.math.tanh(0.04f * ih.offset.x / max)
                    translationY = max * kotlin.math.tanh(0.04f * ih.offset.y / max)
                }
            },
            onDrawSurface = {
                if (tint.isSpecified) {
                    // Tinted glass, for the primary action only: the hue maps onto what's behind.
                    drawRect(tint, blendMode = androidx.compose.ui.graphics.BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.78f))
                } else {
                    drawRect(surface)
                }
            },
        )
        .then(interaction?.let { it.modifier.then(it.gestureModifier) } ?: Modifier)
}

@Composable
fun rememberGlassInteraction(): InteractiveHighlight {
    val scope = rememberCoroutineScope()
    return remember(scope) { InteractiveHighlight(scope) }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassDefaults.Shape,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val highlight = if (onClick != null) rememberGlassInteraction() else null
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .glass(shape, GlassStyle.Card, highlight)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** One tile of a staggered dot grid, repeated across the screen by the GPU. */
private fun dotGridBrush(color: Color, step: Float, radius: Float): Brush {
    val w = step.toInt().coerceAtLeast(2)
    val h = (step * 0.866f * 2).toInt().coerceAtLeast(2)
    val tile = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(tile)
    val paint = androidx.compose.ui.graphics.Paint().apply { this.color = color; isAntiAlias = true }
    canvas.drawCircle(Offset(w / 2f, h / 4f), radius, paint)
    // The offset row, split across the tile edges so the pattern wraps seamlessly.
    canvas.drawCircle(Offset(0f, h * 3 / 4f), radius, paint)
    canvas.drawCircle(Offset(w.toFloat(), h * 3 / 4f), radius, paint)
    return androidx.compose.ui.graphics.ShaderBrush(
        androidx.compose.ui.graphics.ImageShader(tile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated),
    )
}
