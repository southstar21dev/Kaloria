package dev.southstar21.kaloria.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavKey
import com.kyant.shapes.RoundedRectangle
import dev.southstar21.kaloria.R
import dev.southstar21.kaloria.core.designsystem.component.GlassStyle
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.rememberGlassInteraction
import dev.southstar21.kaloria.core.designsystem.liquid.LiquidTabs
import dev.southstar21.kaloria.feature.foods.FoodsKey
import dev.southstar21.kaloria.feature.progress.ProgressKey
import dev.southstar21.kaloria.feature.today.TodayKey

internal val DockHeight = 64.dp

internal enum class TopLevelDestination(val key: NavKey, @StringRes val label: Int, val icon: ImageVector) {
    Today(TodayKey, R.string.dock_today, Icons.Rounded.Today),
    Progress(ProgressKey, R.string.dock_progress, Icons.Rounded.Insights),
    Foods(FoodsKey, R.string.dock_foods, Icons.AutoMirrored.Rounded.MenuBook),
}

internal enum class AddAction(@StringRes val label: Int, val icon: ImageVector) {
    ScanLabel(R.string.dock_add_scan_label, Icons.Rounded.DocumentScanner),
    ScanBarcode(R.string.dock_add_scan_barcode, Icons.Rounded.QrCodeScanner),
    NewFood(R.string.dock_add_new_food, Icons.Rounded.Edit),
    Search(R.string.dock_add_search, Icons.Rounded.Search),
}

/**
 * iOS 26-style floating navigation: a Liquid Glass tab bar whose selection is itself a drop of
 * glass you can press and drag between tabs (it magnifies the tabs beneath with a little chromatic
 * aberration and squashes with speed), plus a separate tinted glass + for the primary action.
 * The add menu pops out of the + as one glass panel.

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
        AddMenu(menuOpen, onPick = {
            onMenuOpenChange(false)
            onAdd(it)
        })
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
    // Composition only cares whether the menu is showing; the per-frame progress is read in the layer.
    val visible by remember { derivedStateOf { progress > 0.001f } }
    if (!visible) return
    Column(
        Modifier
            .padding(bottom = 12.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 1f)
                val s = lerp(0.35f, 1f, progress)
                scaleX = s
                scaleY = s
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
                Text(stringResource(action.label), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun AddButton(open: Boolean, onClick: () -> Unit) {
    val highlight = rememberGlassInteraction()
    val rotation by animateFloatAsState(if (open) 135f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "add")
    val description = stringResource(if (open) R.string.dock_close_add_menu else R.string.dock_add_food)
    Box(
        Modifier
            .size(DockHeight)
            .glass(androidx.compose.foundation.shape.CircleShape, GlassStyle.Control, highlight, tint = MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = rotation })
    }
}

@Composable
private fun LiquidTabBar(selectedIndex: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    LiquidTabs(
        count = TopLevelDestination.entries.size,
        selectedIndex = selectedIndex,
        onSelect = onTabSelected,
        modifier = modifier,
        height = DockHeight,
    ) { index, tinted ->
        val destination = TopLevelDestination.entries[index]
        val content = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        Icon(destination.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Text(stringResource(destination.label), style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
