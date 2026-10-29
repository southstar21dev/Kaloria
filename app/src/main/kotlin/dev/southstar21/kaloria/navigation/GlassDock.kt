package dev.southstar21.kaloria.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavKey
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
import com.kyant.shapes.RoundedRectangle
import dev.southstar21.kaloria.core.designsystem.component.GlassStyle
import dev.southstar21.kaloria.core.designsystem.component.LocalFloatingBackdrop
import dev.southstar21.kaloria.core.designsystem.component.LocalReduceTransparency
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.rememberGlassInteraction
import dev.southstar21.kaloria.core.designsystem.liquid.DampedDragAnimation
import dev.southstar21.kaloria.core.designsystem.liquid.InteractiveHighlight
import dev.southstar21.kaloria.feature.foods.FoodsKey
import dev.southstar21.kaloria.feature.progress.ProgressKey
import dev.southstar21.kaloria.feature.settings.SettingsKey
import dev.southstar21.kaloria.feature.today.TodayKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal val DockHeight = 64.dp

internal enum class TopLevelDestination(val key: NavKey, val label: String, val icon: ImageVector) {
    Today(TodayKey, "Today", Icons.Rounded.Today),
    Progress(ProgressKey, "Progress", Icons.Rounded.Insights),
    Foods(FoodsKey, "Foods", Icons.AutoMirrored.Rounded.MenuBook),
    Settings(SettingsKey, "Settings", Icons.Rounded.Settings),
}

internal enum class AddAction(val label: String, val icon: ImageVector) {
    ScanLabel("Scan nutrition label", Icons.Rounded.DocumentScanner),
    ScanBarcode("Scan barcode", Icons.Rounded.QrCodeScanner),
    NewFood("New food", Icons.Rounded.Edit),
    Search("Search foods", Icons.Rounded.Search),
}

/**
 * iOS 26-style floating navigation: a Liquid Glass tab bar whose selection is itself a drop of
 * glass you can press and drag between tabs (it magnifies the tabs beneath with a little chromatic
 * aberration and squashes with speed), plus a separate tinted glass + for the primary action.
 * The add menu pops out of the + as one glass panel.
 *
 * Tab bar construction adapted from Kyant0/AndroidLiquidGlass's LiquidBottomTabs (Apache-2.0).
 */
@Composable
internal fun BoxScope.GlassDock(
    selected: TopLevelDestination,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSelect: (TopLevelDestination) -> Unit,
    onAdd: (AddAction) -> Unit,
) {
    BackHandler(menuOpen) { onMenuOpenChange(false) }
    val haptics = LocalHapticFeedback.current

    AnimatedVisibility(menuOpen, enter = fadeIn(tween(180)), exit = fadeOut(tween(160)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onMenuOpenChange(false) },
        )
    }

    Column(
        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp).widthIn(max = 460.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AddMenu(menuOpen, onPick = { onMenuOpenChange(false); onAdd(it) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidTabBar(
                selectedIndex = selected.ordinal,
                onTabSelected = { index ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(TopLevelDestination.entries[index])
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            AddButton(menuOpen) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onMenuOpenChange(!menuOpen)
            }
        }
    }
}

/** Menus "simply pop open" from the control (WWDC25): one glass panel growing out of the +. */
@Composable
private fun AddMenu(open: Boolean, onPick: (AddAction) -> Unit) {
    val progress by animateFloatAsState(
        if (open) 1f else 0f,
        if (open) spring(dampingRatio = 0.72f, stiffness = 420f) else spring(dampingRatio = 1f, stiffness = 900f),
        label = "menu",
    )
    if (progress <= 0.001f) return
    Column(
        Modifier
            .padding(bottom = 12.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 1f)
                val s = lerp(0.35f, 1f, progress)
                scaleX = s; scaleY = s
                alpha = progress.coerceIn(0f, 1f)
            }
            .width(250.dp)
            .glass(RoundedRectangle(28.dp), GlassStyle.Bar)
            .padding(vertical = 8.dp),
    ) {
        AddAction.entries.forEach { action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onPick(action) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(action.label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun AddButton(open: Boolean, onClick: () -> Unit) {
    val highlight = rememberGlassInteraction()
    val rotation by animateFloatAsState(if (open) 135f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "add")
    Box(
        Modifier
            .size(DockHeight)
            .glass(androidx.compose.foundation.shape.CircleShape, GlassStyle.Control, highlight, tint = MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (open) "Close add menu" else "Add food" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp).rotate(rotation))
    }
}

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

@Composable
private fun LiquidTabBar(selectedIndex: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val selectedNow by rememberUpdatedState(selectedIndex)
    val onSelect by rememberUpdatedState(onTabSelected)
    val backdrop = LocalFloatingBackdrop.current
    val reduce = LocalReduceTransparency.current
    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.surface.copy(alpha = if (reduce) 0.94f else 0.30f)
    val tabsBackdrop = rememberLayerBackdrop()
    val tabsCount = TopLevelDestination.entries.size

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val scope = rememberCoroutineScope()
        // One state for the bar's lifetime: taps and drags write it, navigation follows it.
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }
        val drag = remember(scope) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = target
                    animateToValue(target.toFloat())
                    scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, amount ->
                    updateValue((targetValue + amount.x / tabWidth).fastCoerceIn(0f, (tabsCount - 1).toFloat()))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                },
            )
        }
        // Follow navigation that didn't come from the bar (back, shortcuts).
        LaunchedEffect(selectedIndex) {
            if (currentIndex != selectedIndex) {
                currentIndex = selectedIndex
                drag.animateToValue(selectedIndex.toFloat())
            }
        }
        LaunchedEffect(drag) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                drag.animateToValue(index.toFloat())
                if (index != selectedNow) onSelect(index)
            }
        }

        val highlight = remember(scope) {
            InteractiveHighlight(scope, position = { size, _ -> Offset((drag.value + 0.5f) * tabWidth + panelOffset, size.height / 2f) })
        }

        @Composable
        fun tabs(tinted: Boolean, rowModifier: Modifier) = Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            TopLevelDestination.entries.forEachIndexed { index, d ->
                TabItem(d, tinted, onClick = { currentIndex = index })
            }
        }

        // The bar: one capsule of glass.
        tabs(
            tinted = false,
            rowModifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        if (reduce) blur(32.dp.toPx()) else {
                            vibrancy(); blur(2.dp.toPx()); lens(24.dp.toPx(), 34.dp.toPx())
                        }
                    },
                    layerBlock = {
                        val s = lerp(1f, 1f + 16.dp.toPx() / size.width, drag.pressProgress)
                        scaleX = s; scaleY = s
                    },
                    shadow = { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f)) },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(DockHeight)
                .fillMaxWidth()
                .padding(4.dp),
        )

        // An invisible accent-tinted copy of the tabs, recorded so the selection lens shows the
        // selected tab in the accent colour as it passes over it.
        CompositionLocalProvider(LocalTabScale provides { lerp(1f, 1.2f, drag.pressProgress) }) {
            tabs(
                tinted = true,
                rowModifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val p = drag.pressProgress
                            vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx() * p, 24.dp.toPx() * p)
                        },
                        highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                        onDrawSurface = { drawRect(container) },
                    )
                    .then(highlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
            )
        }

        // The selection: a drop of glass that magnifies what's under it while held.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer { translationX = drag.value * tabWidth + panelOffset }
                .then(highlight.gestureModifier)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val p = drag.pressProgress
                        lens(10.dp.toPx() * p, 14.dp.toPx() * p, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    shadow = { Shadow(alpha = drag.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * drag.pressProgress, alpha = drag.pressProgress) },
                    layerBlock = {
                        scaleX = drag.scaleX
                        scaleY = drag.scaleY
                        val v = drag.velocity / 10f
                        scaleX /= 1f - (v * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (v * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val p = drag.pressProgress
                        drawRect(accent.copy(alpha = if (dark) 0.22f else 0.14f), alpha = 1f - p)
                        drawRect(Color.Black.copy(alpha = 0.03f * p))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount),
        )
    }
}

@Composable
private fun RowScope.TabItem(destination: TopLevelDestination, tinted: Boolean, onClick: () -> Unit) {
    val scale = LocalTabScale.current
    val content = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .clip(Capsule())
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer { val s = scale(); scaleX = s; scaleY = s },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(destination.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Text(destination.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

