package dev.southstar21.kaloria.feature.today

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Serving
import dev.southstar21.kaloria.core.ui.LocalClock
import dev.southstar21.kaloria.core.ui.PortionSheet
import java.time.LocalDate
import java.time.LocalTime

/**
 * @param onDayChange tells the app which day is shown, so the dock's add menu logs onto it.
 * @param onOpenSettings the round button at the top right.
 */
@Composable
fun TodayRoute(onDayChange: (LocalDate) -> Unit, onOpenSettings: () -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.date) { onDayChange(state.date) }
    val stepsPermission = rememberLauncherForActivityResult(viewModel.stepsPermissionContract()) { viewModel.refreshSteps() }
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    TodayScreen(
        state = state,
        greeting = stringResource(greetingFor(LocalTime.now(LocalClock.current))),
        steps = viewModel.steps,
        onConnectSteps = { stepsPermission.launch(viewModel.stepsPermissions) },
        servingsFor = viewModel::servingsFor,
        onPreviousDay = viewModel::previousDay,
        onNextDay = viewModel::nextDay,
        onToday = viewModel::goToToday,
        onSelectDay = viewModel::selectDay,
        onOpenSettings = onOpenSettings,
        onUpdateEntry = viewModel::updateEntry,
        onDeleteEntry = viewModel::deleteEntry,
        onSaveServings = viewModel::saveServings,
    )
}

@StringRes
internal fun greetingFor(time: LocalTime) = when (time.hour) {
    in 5..11 -> R.string.today_greeting_morning
    in 12..17 -> R.string.today_greeting_afternoon
    else -> R.string.today_greeting_evening
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TodayScreen(
    state: TodayUiState,
    greeting: String,
    steps: StepsState,
    onConnectSteps: () -> Unit,
    servingsFor: suspend (LogEntry) -> List<Serving> = { emptyList() },
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateEntry: (LogEntry, Double, java.time.LocalTime) -> Unit,
    onDeleteEntry: (LogEntry) -> Unit,
    onSaveServings: (foodId: String, List<Serving>) -> Unit = { _, _ -> },
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val intro = rememberIntro()
    val haptics = LocalHapticFeedback.current
    val swipeThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    // The swipe handler outlives recompositions, so it reads the latest callbacks through these.
    val previousDay by rememberUpdatedState(onPreviousDay)
    val nextDay by rememberUpdatedState(onNextDay)
    val previousDayLabel = stringResource(R.string.today_previous_day)
    val nextDayLabel = stringResource(R.string.today_next_day)
    val zone = LocalClock.current.zone
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .glassSource()
            // The swipe below, for screen readers and switch access.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousDayLabel) {
                        previousDay()
                        true
                    },
                    CustomAccessibilityAction(nextDayLabel) {
                        nextDay()
                        true
                    },
                )
            }
            // Swipe sideways anywhere to move between days.
            .pointerInput(swipeThreshold, haptics) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > swipeThreshold) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            previousDay()
                        }
                        if (drag < -swipeThreshold) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            nextDay()
                        }
                    },
                ) { _, amount -> drag += amount }
            },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = dock + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") { Header(state, greeting, onToday, onOpenSettings) }
        item(key = "week") {
            WeekStrip(
                selected = state.date,
                today = state.today,
                onSelect = { day ->
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelectDay(day)
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        item(key = "energy") {
            Row(Modifier.introItem(intro, 0).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaloriesTile(state, Modifier.weight(1f).fillMaxHeight())
                MacrosTile(state, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "activity") {
            Row(Modifier.introItem(intro, 1).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (steps.access != HealthAccess.Unsupported) StepsTile(steps, state.stepsGoal, onConnectSteps, Modifier.weight(1f).fillMaxHeight())
                FiberTile(state.totals.fiber ?: 0.0, state.goals.fiber, Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "logged") { LoggedTile(state, onEdit = { editing = it.id }, Modifier.introItem(intro, 2)) }
        item(key = "micros") { MicroCard(state.totals, state.goals.micros, Modifier.introItem(intro, 3)) }
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
            initialTime = entry.loggedAt.atZone(zone).toLocalTime(),
            confirmLabel = stringResource(R.string.today_save),
            onConfirm = { amount, time ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onUpdateEntry(entry, amount, time)
                editing = null
            },
            onDismiss = { editing = null },
            onDelete = {
                onDeleteEntry(entry)
                editing = null
            },
            onServingsChange = entry.foodId?.let { id -> { onSaveServings(id, it) } },
        )
    }
}
