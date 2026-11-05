package dev.southstar21.kaloria.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.southstar21.kaloria.core.designsystem.theme.Brand

/**
 * A card of content as frosted glass. Real glass re-renders what's behind it through blur and
 * lens shaders on every scroll frame, which is why it's kept to the navigation layer. Here the
 * card draws its own slice of the field's pre-blurred copy ([FrostedField]) under a milky tint,
 * with a lit rim and a top sheen: the frosted look for one small texture draw.
 *
 * @param iridescent faint pastel glints, for the one or two hero tiles on a screen.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassDefaults.Shape,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    iridescent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, pressedScale = 0.98f) else Modifier)
            .frostedGlass(shape, iridescent = iridescent)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Frosted glass over the app's silver field, as cards, tiles and sheets use it: the field's
 * pre-blurred copy ([FrostedField]) at this spot, a milky [tint], a lit rim and a top sheen. It
 * draws from the field image rather than reading what's behind, so it also works in windows of
 * its own (bottom sheets) and over the camera.
 *
 * @param iridescent faint pastel glints, for the one or two hero surfaces on a screen.
 */
@Composable
fun Modifier.frostedGlass(shape: Shape, tint: Color = MilkyTint, iridescent: Boolean = false): Modifier {
    val reduce = LocalReduceTransparency.current
    val frost = LocalFrostedField.current
    val solid = MaterialTheme.colorScheme.surfaceContainerLow
    // Read only while drawing, so scrolling redraws the frost without recomposing.
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position = it.positionInWindow() }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val rim = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.95f),
                0.5f to Color.White.copy(alpha = 0.55f),
                1f to Color.White.copy(alpha = 0.75f),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.22f),
                1f to Color.Transparent,
                endY = size.height.coerceAtMost(72.dp.toPx()),
            )
            val glints = if (iridescent) {
                listOf(
                    Brush.radialGradient(listOf(Brand.GlintPink.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.8f, size.height * 0.3f), size.minDimension * 0.6f),
                    Brush.radialGradient(listOf(Brand.GlintBlue.copy(alpha = 0.5f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.8f), size.minDimension * 0.55f),
                    Brush.radialGradient(listOf(Brand.GlintGold.copy(alpha = 0.35f), Color.Transparent), Offset(size.width * 0.6f, size.height * 0.6f), size.minDimension * 0.4f),
                )
            } else {
                emptyList()
            }
            val stroke = Stroke(2.dp.toPx())
            onDrawWithContent {
                val image = frost?.image
                if (reduce) {
                    drawRect(solid)
                } else if (image == null) {
                    // The field's frosted copy is still being baked (the first frames after start):
                    // the tint alone over the field looks nearly the same, without a flash.
                    drawRect(tint)
                    glints.forEach { drawRect(it) }
                    drawRect(sheen)
                } else {
                    val origin = frost.origin
                    translate(origin.x - position.x, origin.y - position.y) {
                        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset.Zero, frost.fieldSize, filterQuality = FilterQuality.Low)
                    }
                    drawRect(tint)
                    glints.forEach { drawRect(it) }
                    drawRect(sheen)
                }
                drawContent()
                drawOutline(outline, rim, style = stroke)
            }
        }
}

/** The milky white of frosted glass over the silver field. */
val MilkyTint = Color.White.copy(alpha = 0.34f)

/** A small white note inside a tile, like the stacked cards in a widget. */
@Composable
fun NoteChip(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A small uppercase heading above a group of cards, as in iOS grouped lists. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() }.padding(start = 8.dp, top = 8.dp),
    )
}

/** A big two-weight screen header: bold [title], then a light [subtitle], as in a widget launcher. */
@Composable
fun GreetingHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) { heading() }.padding(horizontal = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        Text(
            subtitle,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
