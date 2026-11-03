package dev.southstar21.kaloria.navigation

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kyant.backdrop.backdrops.layerBackdrop
import dev.southstar21.kaloria.core.designsystem.component.FloatingGlassScope
import dev.southstar21.kaloria.core.designsystem.component.GlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.rememberGlassLayer
import dev.southstar21.kaloria.core.ui.LocalClock
import dev.southstar21.kaloria.feature.foods.FoodEditorKey
import dev.southstar21.kaloria.feature.foods.FoodsKey
import dev.southstar21.kaloria.feature.foods.editor.FoodEditorRoute
import dev.southstar21.kaloria.feature.foods.list.FoodsRoute
import dev.southstar21.kaloria.feature.foodsearch.FoodSearchKey
import dev.southstar21.kaloria.feature.foodsearch.FoodSearchRoute
import dev.southstar21.kaloria.feature.progress.ProgressKey
import dev.southstar21.kaloria.feature.progress.ProgressRoute
import dev.southstar21.kaloria.feature.scan.BarcodeScanKey
import dev.southstar21.kaloria.feature.scan.LabelScanKey
import dev.southstar21.kaloria.feature.scan.barcode.BarcodeScanRoute
import dev.southstar21.kaloria.feature.scan.label.LabelScanRoute
import dev.southstar21.kaloria.feature.settings.SettingsKey
import dev.southstar21.kaloria.feature.settings.SettingsRoute
import dev.southstar21.kaloria.feature.today.TodayKey
import dev.southstar21.kaloria.feature.today.TodayRoute
import dev.southstar21.kaloria.widget.ACTION_SCAN_BARCODE
import dev.southstar21.kaloria.widget.ACTION_SEARCH_FOOD
import java.time.LocalDate

/** Places the app can be opened directly from outside, e.g. the home screen widget. */
enum class Shortcut {
    ScanBarcode,
    SearchFood,
    ;

    companion object {
        fun from(intent: Intent?) = when (intent?.action) {
            ACTION_SCAN_BARCODE -> ScanBarcode
            ACTION_SEARCH_FOOD -> SearchFood
            else -> null
        }
    }
}

@Composable
fun KaloriaNavDisplay(shortcut: Shortcut? = null, onShortcutHandled: () -> Unit = {}) {
    val backStack = rememberNavBackStack(TodayKey)
    val clock = LocalClock.current
    LaunchedEffect(shortcut) {
        val today = LocalDate.now(clock).toEpochDay()
        when (shortcut) {
            Shortcut.ScanBarcode -> backStack.openOverToday(BarcodeScanKey(today))
            Shortcut.SearchFood -> backStack.openOverToday(FoodSearchKey(today))
            null -> return@LaunchedEffect
        }
        onShortcutHandled()
    }
    val current = backStack.lastOrNull()
    val topLevel = TopLevelDestination.entries.firstOrNull { it.key == current }
    // Which screen was on top before this change decides how the change moves. Plain fields, not
    // state, so updating them here doesn't trigger another composition.
    val motion = remember { NavMotion() }
    motion.update(current)
    // The dock keeps showing the last tab while it slides away.
    val lastTab = remember { mutableStateOf(TopLevelDestination.Today) }
    SideEffect { if (topLevel != null) lastTab.value = topLevel }
    val dockTab = topLevel ?: lastTab.value
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(topLevel) { if (topLevel == null) menuOpen = false }
    // The day the Today tab is showing; additions from the dock go there. Only read when adding,
    // never during composition, so swiping between days doesn't recompose the whole app shell.
    val todayDay = rememberSaveable { mutableLongStateOf(LocalDate.now(clock).toEpochDay()) }
    fun addDay() = if (backStack.lastOrNull() == TodayKey) todayDay.longValue else LocalDate.now(clock).toEpochDay()
    val decorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator())

    GlassBackdrop {
        // Everything the screens draw is recorded so the floating dock refracts it.
        val screens = rememberGlassLayer()
        CompositionLocalProvider(LocalDockPadding provides navBar + DockHeight + 12.dp) {
            NavDisplay(
                modifier = Modifier.layerBackdrop(screens),
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = motion.forward(),
                popTransitionSpec = motion.back(),
                predictivePopTransitionSpec = motion.predictiveBack(),
                entryDecorators = decorators,
                entryProvider = entryProvider {
                    entry<TodayKey> {
                        TodayRoute(onDayChange = { todayDay.longValue = it.toEpochDay() }, onOpenSettings = { backStack.add(SettingsKey) })
                    }
                    entry<FoodSearchKey> { key ->
                        FoodSearchRoute(
                            key = key,
                            onBack = { backStack.removeLastOrNull() },
                            onCreateFood = { name -> backStack.add(FoodEditorKey(name = name.ifBlank { null })) },
                            onScanBarcode = { backStack.add(BarcodeScanKey(key.epochDay)) },
                        )
                    }
                    entry<BarcodeScanKey> { key ->
                        BarcodeScanRoute(
                            key = key,
                            onBack = { backStack.removeLastOrNull() },
                            onLogged = { backStack.removeLastOrNull() },
                            onScanLabel = { code -> backStack.replaceLast(LabelScanKey(code)) },
                            onEnterManually = { code -> backStack.replaceLast(FoodEditorKey(barcode = code)) },
                        )
                    }
                    entry<LabelScanKey> { key ->
                        LabelScanRoute(
                            key = key,
                            onBack = { backStack.removeLastOrNull() },
                            onReview = { draftId, barcode -> backStack.replaceLast(FoodEditorKey(barcode = barcode, draftId = draftId)) },
                        )
                    }
                    entry<FoodsKey> {
                        FoodsRoute(onOpenFood = { id -> backStack.add(FoodEditorKey(foodId = id)) })
                    }
                    entry<FoodEditorKey> { key ->
                        FoodEditorRoute(key = key, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<SettingsKey> { SettingsRoute(onBack = { backStack.removeLastOrNull() }) }
                    entry<ProgressKey> { ProgressRoute() }
                },
            )
        }

        // Slides down out of the way under screens opened on top, and springs back on return.
        AnimatedVisibility(
            visible = topLevel != null,
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 6 } + fadeIn(tween(180)),
            exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 600f)) { it / 6 } + fadeOut(tween(140)),
        ) {
            Box(Modifier.fillMaxSize()) {
                FloatingGlassScope(screens) {
                    GlassDock(
                        selected = dockTab,
                        menuOpen = menuOpen,
                        onMenuOpenChange = { menuOpen = it },
                        onSelect = { backStack.navigateTopLevel(it.key) },
                        onAdd = { action ->
                            backStack.add(
                                when (action) {
                                    AddAction.ScanLabel -> LabelScanKey()
                                    AddAction.ScanBarcode -> BarcodeScanKey(addDay())
                                    AddAction.NewFood -> FoodEditorKey()
                                    AddAction.Search -> FoodSearchKey(addDay())
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun NavBackStack<NavKey>.openOverToday(key: NavKey) {
    clear()
    add(TodayKey)
    add(key)
}

private fun NavBackStack<NavKey>.replaceLast(key: NavKey) {
    removeLastOrNull()
    add(key)
}

/** Switching tabs resets to that tab's root; Today always stays at the bottom so back exits from it. */
private fun NavBackStack<NavKey>.navigateTopLevel(key: NavKey) {
    if (lastOrNull() == key) return
    clear()
    add(TodayKey)
    if (key != TodayKey) add(key)
}
