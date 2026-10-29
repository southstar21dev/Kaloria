package dev.southstar21.kaloria.feature.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import dev.southstar21.kaloria.core.health.StepsAccess
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.southstar21.kaloria.core.designsystem.component.AnimatedCount
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import dev.southstar21.kaloria.core.model.Serving
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.NutrientBar
import dev.southstar21.kaloria.core.designsystem.component.ProgressRing
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.theme.MacroColors
import dev.southstar21.kaloria.core.designsystem.theme.TrackerColors
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.ui.PortionSheet
import dev.southstar21.kaloria.core.ui.dayTitle
import dev.southstar21.kaloria.core.ui.gramsText
import dev.southstar21.kaloria.core.ui.kcalText
import dev.southstar21.kaloria.core.ui.timeText
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** @param onDayChange tells the app which day is shown, so the dock's add menu logs onto it. */
@Composable
fun TodayRoute(onDayChange: (LocalDate) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.date) { onDayChange(state.date) }
    val stepsPermission = rememberLauncherForActivityResult(viewModel.stepsPermissionContract()) { viewModel.refreshSteps() }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    TodayScreen(
        state = state,
        steps = viewModel.steps,
        onConnectSteps = { stepsPermission.launch(viewModel.stepsPermissions) },
        servingsFor = viewModel::servingsFor,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onToday = viewModel::goToToday,
        onAddWater = viewModel::addWater,
        onUndoWater = viewModel::undoWater,
        onUpdateEntry = viewModel::updateEntry,
        onDeleteEntry = viewModel::deleteEntry,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TodayScreen(
    state: TodayUiState,
    steps: StepsState,
    onConnectSteps: () -> Unit,
    servingsFor: suspend (LogEntry) -> List<Serving> = { emptyList() },
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onAddWater: (Int) -> Unit,
    onUndoWater: () -> Unit,
    onUpdateEntry: (LogEntry, Double, java.time.LocalTime) -> Unit,
    onDeleteEntry: (LogEntry) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val intro = rememberIntro()
    val haptics = LocalHapticFeedback.current
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassSource()
                // Swipe sideways anywhere to move between days.
                .pointerInput(Unit) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag > swipeThreshold) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPreviousDay() }
                            if (drag < -swipeThreshold) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onNextDay() }
                        },
                    ) { _, amount -> drag += amount }
                },
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = dock + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { DayHeader(state, onPreviousDay, onNextDay, onToday) }
            item(key = "energy") { EnergyCard(state, Modifier.introItem(intro, 0)) }
            item(key = "macros") { MacroCard(state, Modifier.introItem(intro, 1)) }
            item(key = "micros") { MicroCard(state.totals, state.goals.micros, Modifier.introItem(intro, 2)) }
            item(key = "water") {
                WaterCard(
                    state,
                    onAdd = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onAddWater(it) },
                    onUndo = onUndoWater,
                    modifier = Modifier.introItem(intro, 3),
                )
            }
            if (steps.access != StepsAccess.Unsupported) {
                item(key = "steps") { StepsCard(steps, state.stepsGoal, onConnectSteps, Modifier.introItem(intro, 4)) }
            }
            item(key = "timeline") {
                Text(
                    "Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            if (state.entries.isEmpty() && !state.loading) {
                item(key = "empty") {
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Nothing logged yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tap + below to search, scan a barcode or a nutrition label.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.entries, key = { it.id }) { entry ->
                TimelineRow(entry, onClick = { editing = entry.id }, modifier = Modifier.animateItem())
            }
        }

    }

    state.entries.firstOrNull { it.id == editing }?.let { entry ->
        val servings by produceState(emptyList<Serving>(), entry.id) { value = servingsFor(entry) }
        PortionSheet(
            title = entry.foodName,
            subtitle = entry.brand,
            unit = entry.unit,
            per100 = entry.nutrientsPer100,
            servings = servings,
            initialAmount = entry.amount,
            initialTime = entry.loggedAt.atZone(ZoneId.systemDefault()).toLocalTime(),
            confirmLabel = "Save",
            onConfirm = { amount, time ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onUpdateEntry(entry, amount, time); editing = null
            },
            onDismiss = { editing = null },
            onDelete = { onDeleteEntry(entry); editing = null },
        )
    }
}

@Composable
private fun DayHeader(state: TodayUiState, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day") }
        AnimatedContent(
            targetState = state.date,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 400f)) { if (forward) it / 3 else -it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally(spring(dampingRatio = 0.8f, stiffness = 400f)) { if (forward) -it / 3 else it / 3 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "day",
        ) { day ->
            Text(
                day.dayTitle(state.today),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.clickable(onClick = onToday),
            )
        }
        if (state.date != state.today) {
            TextButton(onClick = onToday) { Text("Today") }
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day") }
    }
}

@Composable
private fun EnergyCard(state: TodayUiState, modifier: Modifier = Modifier) {
    val eaten = state.totals.energyKcal ?: 0.0
    val goal = state.goals.energyKcal
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (goal != null && goal > 0) (eaten / goal).toFloat() else 0f,
                modifier = Modifier.size(168.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val remaining = goal?.let { it - eaten }
                    AnimatedCount(
                        value = kotlin.math.abs(remaining ?: eaten).roundToInt(),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        when {
                            remaining == null -> "kcal eaten"
                            remaining >= 0 -> "kcal left"
                            else -> "kcal over"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (remaining != null && remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Eaten", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AnimatedCount(eaten.roundToInt(), MaterialTheme.typography.titleMedium, format = { "%,d kcal".format(it) })
                }
                Stat("Goal", goal?.let { "${"%,d".format(it)} kcal" } ?: "Not set")
                Stat("Entries", state.entries.size.toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MacroCard(state: TodayUiState, modifier: Modifier = Modifier) {
    val t = state.totals
    val g = state.goals
    GlassCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NutrientBar("Protein", t.protein ?: 0.0, g.protein, "g", MacroColors.Protein)
            NutrientBar("Carbs", t.carbohydrates ?: 0.0, g.carbohydrates, "g", MacroColors.Carbs)
            NutrientBar("Fat", t.fat ?: 0.0, g.fat, "g", MacroColors.Fat)
            NutrientBar("Fiber", t.fiber ?: 0.0, g.fiber, "g", MacroColors.Fiber)
        }
    }
}

@Composable
private fun MicroCard(totals: Nutrients, targets: Map<Micronutrient, Double>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rows = buildList {
        totals.sugars?.let { add(Triple("Sugars", it.gramsText(), null)) }
        totals.saturatedFat?.let { add(Triple("Saturated fat", it.gramsText(), null)) }
        totals.salt?.let { add(Triple("Salt", it.gramsText(), null)) }
        Micronutrient.entries.forEach { m ->
            val value = totals.micros[m] ?: if (m == Micronutrient.Sodium) totals.sodiumMg else null
            if (value != null || targets[m] != null) {
                val target = targets[m]?.let { " / ${it.gramsText(m.unit.symbol)}" }.orEmpty()
                add(Triple(m.label, (value ?: 0.0).gramsText(m.unit.symbol) + target, value))
            }
        }
    }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.6f), label = "chevron")
    GlassCard(modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Micronutrients", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "${rows.size} tracked",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(chevron))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rows.isEmpty()) {
                    Text(
                        "No micronutrient data in today's foods.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                rows.forEach { (label, value, _) ->
                    Row {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterCard(state: TodayUiState, onAdd: (Int) -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val goal = state.goals.waterMl
    // The drop bounces each time water is added.
    val drop = remember { Animatable(1f) }
    var lastMl by remember { mutableIntStateOf(state.waterMl) }
    LaunchedEffect(state.waterMl) {
        if (state.waterMl > lastMl) {
            drop.snapTo(1.35f)
            drop.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f))
        }
        lastMl = state.waterMl
    }
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (goal != null && goal > 0) state.waterMl.toFloat() / goal else 0f,
                modifier = Modifier.size(56.dp),
                color = TrackerColors.Water,
                overColor = TrackerColors.Water,
                strokeWidth = 6.dp,
            ) {
                Icon(Icons.Rounded.WaterDrop, null, tint = TrackerColors.Water, modifier = Modifier.graphicsLayer { scaleX = drop.value; scaleY = drop.value })
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Water", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AnimatedCount(
                    value = state.waterMl,
                    style = MaterialTheme.typography.titleMedium,
                    format = { ml -> "%.2f".format(ml / 1000.0) + (goal?.let { " / ${"%.1f".format(it / 1000.0)}" } ?: "") + " L" },
                )
            }
            if (state.water.isNotEmpty()) {
                IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo last water") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(250, 330, 500).forEach { ml ->
                FilledTonalButton(onClick = { onAdd(ml) }, modifier = Modifier.weight(1f)) { Text("+$ml ml") }
            }
        }
    }
}

@Composable
private fun StepsCard(steps: StepsState, goal: Int?, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = if (goal != null && goal > 0 && steps.count != null) steps.count.toFloat() / goal else 0f,
                modifier = Modifier.size(56.dp),
                color = TrackerColors.Steps,
                overColor = TrackerColors.Steps,
                strokeWidth = 6.dp,
            ) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = TrackerColors.Steps)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Steps", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (steps.access) {
                    StepsAccess.Granted -> AnimatedCount(
                        value = (steps.count ?: 0).toInt(),
                        style = MaterialTheme.typography.titleMedium,
                        format = { "%,d".format(it) + (goal?.let { g -> " / ${"%,d".format(g)}" } ?: "") },
                    )
                    StepsAccess.NotInstalled -> Text("Update Health Connect to see steps", style = MaterialTheme.typography.bodyMedium)
                    else -> Text("From Samsung Health via Health Connect", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (steps.access == StepsAccess.NeedsPermission) {
                FilledTonalButton(onClick = onConnect) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: LogEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = entry.nutrients
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.loggedAt.timeText(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.foodName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.amount.roundToInt()} ${entry.unit.symbol} · P ${n.protein.gramsText()} · C ${n.carbohydrates.gramsText()} · F ${n.fat.gramsText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(n.energyKcal.kcalText(), style = MaterialTheme.typography.titleMedium)
        }
    }
}
