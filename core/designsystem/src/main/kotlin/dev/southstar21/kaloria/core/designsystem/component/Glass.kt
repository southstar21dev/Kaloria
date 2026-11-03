package dev.southstar21.kaloria.core.designsystem.component

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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import dev.southstar21.kaloria.core.designsystem.theme.KaloriaCameraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/** Glass over a live camera: nothing can be captured behind it, so it's tinted, not refracting. Set it with [GlassOverCamera]. */
val LocalGlassOverCamera = staticCompositionLocalOf { false }

/**
 * Content over a live camera feed. The glass there is dark tinted rather than refracting, so the
 * dark scheme is used in either theme to keep text and icons light on it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlassOverCamera(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = KaloriaCameraColors,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(
            LocalGlassOverCamera provides true,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}

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
    internal val dispersion: Boolean,
    internal val surfaceLight: Float,
    internal val surfaceDark: Float,
    internal val shadowRadius: Dp,
) {
    /**
     * Content cards: the dock's lens and tint, refracting only the colour field behind them. No
     * blur or shadow (the shadow is a blur too): a screen holds many cards, and each re-renders on
     * every scroll frame, so two blurs per card made scrolling stutter.
     */
    Card(blur = 0.dp, refractionHeight = 24.dp, refractionAmount = 34.dp, depth = false, dispersion = false, surfaceLight = 0.30f, surfaceDark = 0.30f, shadowRadius = 0.dp),

    /** Floating bars, matching the dock. */
    Bar(blur = 2.dp, refractionHeight = 24.dp, refractionAmount = 34.dp, depth = false, dispersion = false, surfaceLight = 0.30f, surfaceDark = 0.30f, shadowRadius = 24.dp),

    /** Small controls: buttons, chips, menu items. */
    Control(blur = 2.dp, refractionHeight = 14.dp, refractionAmount = 28.dp, depth = false, dispersion = false, surfaceLight = 0.26f, surfaceDark = 0.32f, shadowRadius = 16.dp),
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
    val frost = remember { FrostedField() }
    // Glass panels aren't Material Surfaces, so nothing else sets the content color: without this,
    // text and icons fall back to black, which is unreadable in dark theme.
    CompositionLocalProvider(
        LocalGlassBackdrop provides field,
        LocalFloatingBackdrop provides field,
        LocalLightSource provides light,
        LocalFrostedField provides frost,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier.fillMaxSize()) {
            SilverField(Modifier.fillMaxSize().onGloballyPositioned { frost.origin = it.positionInWindow() }.layerBackdrop(field), frost)
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

/**
 * A blurrier, low-resolution copy of the field. Content cards draw their own slice of it (offset by
 * where they sit on screen) under a milky tint, which looks like frosted glass over the field for
 * the price of one small texture draw, instead of a live blur.
 */
@Stable
class FrostedField internal constructor() {
    internal var image by mutableStateOf<ImageBitmap?>(null)
    internal var fieldSize by mutableStateOf(IntSize.Zero)
    internal var origin by mutableStateOf(Offset.Zero)
}

val LocalFrostedField = staticCompositionLocalOf<FrostedField?> { null }

/** A soft fabric fold: a radial glow stretched along one axis and tilted. Positions are fractions of the field. */
private class Fold(val x: Float, val y: Float, val radius: Float, val stretch: Float, val degrees: Float, val color: Color, val alpha: Float)

private val Folds = listOf(
    Fold(0.50f, 0.00f, 0.80f, 0.60f, 0f, Color.White, 0.35f),
    Fold(0.62f, 0.42f, 0.32f, 2.6f, 18f, Brand.FoldShade, 0.85f),
    Fold(0.80f, 0.36f, 0.22f, 3.0f, 18f, Brand.FoldLight, 0.95f),
    Fold(0.30f, 0.62f, 0.28f, 2.4f, -12f, Brand.FoldShadeSoft, 0.70f),
    Fold(0.16f, 0.55f, 0.20f, 2.8f, -12f, Brand.FoldLight, 0.85f),
    Fold(0.86f, 0.90f, 0.30f, 1.6f, 30f, Brand.FoldLight, 0.70f),
)

/** Silvery periwinkle with soft fabric folds, painted to whatever size the scope has. */
private fun DrawScope.paintField(softness: Float = 1f) {
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(0f to Brand.SilverTop, 0.55f to Brand.SilverMid, 1f to Brand.SilverLow, start = Offset.Zero, end = Offset(w * 0.55f, h)))
    Folds.forEach { f ->
        val c = Offset(f.x * w, f.y * h)
        val radius = f.radius * w * softness
        withTransform({
            rotate(f.degrees, c)
            scale(1f, f.stretch, c)
        }) {
            drawCircle(Brush.radialGradient(listOf(f.color.copy(alpha = f.alpha), f.color.copy(alpha = 0f)), center = c, radius = radius), radius, c)
        }
    }
}

@Composable
private fun SilverField(modifier: Modifier, frost: FrostedField) {
    // Static, so baked into bitmaps once per size: every glass element redraws the field behind it
    // each frame, and a bitmap is a single texture draw. Baking is software drawing, so it runs off
    // the main thread; until it's done the field is drawn directly, which looks the same.
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val field by produceState<ImageBitmap?>(null, size) {
        if (size == IntSize.Zero) return@produceState
        val (fieldImage, frosted) = withContext(Dispatchers.Default) { bakeField(size, density, layoutDirection) }
        value = fieldImage
        frost.image = frosted
        frost.fieldSize = size
    }
    Box(
        modifier
            .onSizeChanged { size = it }
            .drawBehind {
                val image = field
                if (image != null && image.width == this.size.width.toInt() && image.height == this.size.height.toInt()) {
                    drawImage(image)
                } else {
                    paintField()
                }
            },
    )
}

/** The field at full size, and the frosted copy: a fraction of the resolution with wider folds, upscaled smoothly. */
private fun bakeField(size: IntSize, density: Density, layoutDirection: LayoutDirection): Pair<ImageBitmap, ImageBitmap> {
    val w = size.width.coerceAtLeast(1)
    val h = size.height.coerceAtLeast(1)
    val field = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, layoutDirection, Canvas(field), Size(w.toFloat(), h.toFloat())) { paintField() }
    val small = Size((w / FROST_DOWNSCALE).coerceAtLeast(1).toFloat(), (h / FROST_DOWNSCALE).coerceAtLeast(1).toFloat())
    val frosted = ImageBitmap(small.width.toInt(), small.height.toInt())
    CanvasDrawScope().draw(density, layoutDirection, Canvas(frosted), small) { paintField(softness = 1.3f) }
    return field to frosted
}

private const val FROST_DOWNSCALE = 12

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
        overCamera -> Color.Black.copy(alpha = 0.40f)
        reduce -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        else -> base.copy(alpha = if (dark) style.surfaceDark else style.surfaceLight)
    }
    val rim = Color.White.copy(alpha = if (dark) 0.55f else 0.85f)
    val shadowColor = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f)
    // Light falls from above: a faint sheen across the top of the material gives it thickness.
    val sheen = if (reduce) Color.Transparent else Color.White.copy(alpha = if (dark || overCamera) 0.07f else 0.16f)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { glassShape },
            effects = {
                if (reduce) {
                    blur(32.dp.toPx())
                } else {
                    vibrancy()
                    if (style.blur > 0.dp) blur(style.blur.toPx())
                    lens(
                        style.refractionHeight.toPx(),
                        style.refractionAmount.toPx(),
                        depthEffect = style.depth,
                        chromaticAberration = style.dispersion,
                    )
                }
            },
            highlight = {
                Highlight(
                    width = 0.75.dp,
                    alpha = if (reduce) 0.4f else 1f,
                    style = HighlightStyle.Default(color = rim, angle = light.angle),
                )
            },
            shadow = if (style.shadowRadius > 0.dp) ({ Shadow(radius = style.shadowRadius, color = shadowColor) }) else null,
            layerBlock = interaction?.let { ih ->
                {
                    // Gel-like: swells a few dp and leans toward the finger while pressed.
                    val p = ih.pressProgress
                    val s = lerp(1f, 1f + 6.dp.toPx() / size.height.coerceAtLeast(1f), p).coerceAtMost(1.04f)
                    scaleX = s
                    scaleY = s
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
                if (sheen.alpha > 0f) {
                    drawRect(Brush.verticalGradient(0f to sheen, 0.45f to Color.Transparent, endY = size.height.coerceAtMost(96.dp.toPx())))
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
