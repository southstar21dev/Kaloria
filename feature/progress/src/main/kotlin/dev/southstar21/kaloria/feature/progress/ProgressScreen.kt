package dev.southstar21.kaloria.feature.progress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.BarChart
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.GreetingHeader
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.LocalGlassBackdrop
import dev.southstar21.kaloria.core.designsystem.component.NutrientBar
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import dev.southstar21.kaloria.core.designsystem.liquid.LiquidTabs
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.ui.LocalClock
import dev.southstar21.kaloria.core.ui.gramsText
import dev.southstar21.kaloria.core.ui.kcalText

@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val healthPermission = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) { viewModel.refresh() }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    ProgressScreen(
        state = state,
        steps = viewModel.steps,
        weightAccess = viewModel.weightAccess.collectAsStateWithLifecycle().value,
        syncingWeight = viewModel.syncingWeight.collectAsStateWithLifecycle().value,
        onRange = viewModel::setRange,
        onLogWeight = viewModel::logWeight,
        onDeleteWeight = viewModel::deleteWeight,
        onConnectHealth = { healthPermission.launch(viewModel.healthPermissions) },
    )
}

@Composable
internal fun ProgressScreen(
    state: ProgressUiState?,
    steps: List<Long?>?,
    weightAccess: HealthAccess?,
    syncingWeight: Boolean,
    onRange: (ProgressRange) -> Unit,
    onLogWeight: (Double) -> Unit,
    onDeleteWeight: (String) -> Unit,
    onConnectHealth: () -> Unit,
) {
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var loggingWeight by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val intro = rememberIntro()
    // Up to 90 formatted dates: worked out once per range, not on every recomposition.
    val days = state?.summary?.days
    val formats = rememberProgressFormats()
    val labels = remember(days, formats) { days?.let { axisLabels(it, formats) }.orEmpty() }
    val zone = LocalClock.current.zone
    val grams = stringResource(R.string.progress_grams_unit)

    LazyColumn(
        modifier = Modifier.fillMaxSize().glassSource(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = dock + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            val streak = state?.streak
            GreetingHeader(
                stringResource(R.string.progress_title),
                when {
                    streak == null -> " "
                    streak.current == 0 -> stringResource(R.string.progress_streak_start)
                    streak.loggedToday -> stringResource(R.string.progress_streak_days, streak.current)
                    else -> stringResource(R.string.progress_streak_keep, streak.current)
                },
            )
        }
        item(key = "range") {
            val selected = state?.range ?: ProgressRange.Week
            LiquidTabs(
                count = ProgressRange.entries.size,
                selectedIndex = selected.ordinal,
                onSelect = { onRange(ProgressRange.entries[it]) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                height = 52.dp,
                // It scrolls with the content, so it refracts only the field, never itself.
                backdrop = LocalGlassBackdrop.current,
                floating = false,
            ) { index, tinted ->
                Text(
                    ProgressRange.entries[index].label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (state == null) return@LazyColumn
        val s = state.summary

        item(key = "streak") { StreakTile(state, Modifier.introItem(intro, 0)) }

        item(key = "weight") {
            WeightTile(
                state = state,
                access = weightAccess,
                syncing = syncingWeight,
                onLog = { loggingWeight = true },
                onConnect = onConnectHealth,
                onPick = { deleting = it.id },
                modifier = Modifier.introItem(intro, 1),
            )
        }

        item(key = "energy") {
            ContentCard(Modifier.introItem(intro, 2).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.progress_calories_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.progress_days_logged, s.loggedDays, s.days.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(s.average.energyKcal.kcalText(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text(stringResource(R.string.progress_kcal_per_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                BarChart(
                    values = s.energy,
                    goal = state.goals.energyKcal?.toDouble(),
                    labels = labels,
                    description = stringResource(R.string.progress_calories_chart_description, s.average.energyKcal.kcalText()),
                )
            }
        }

        if (steps != null) {
            item(key = "steps") {
                ContentCard(Modifier.introItem(intro, 3).fillMaxWidth()) {
                    val counted = steps.filterNotNull()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.progress_steps_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            if (counted.isEmpty()) "–" else stringResource(R.string.progress_steps_per_day, counted.average().toLong()),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BarChart(
                        values = steps.map { it?.toDouble() },
                        goal = state.goals.steps?.toDouble(),
                        overColor = MaterialTheme.colorScheme.primary,
                        labels = labels,
                        description = stringResource(R.string.progress_steps_chart_description),
                    )
                }
            }
        }

        item(key = "averages") {
            ContentCard(Modifier.introItem(intro, 4).fillMaxWidth()) {
                Text(stringResource(R.string.progress_averages_title), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutrientBar(stringResource(R.string.progress_protein), s.average.protein ?: 0.0, state.goals.protein, grams)
                    NutrientBar(stringResource(R.string.progress_carbs), s.average.carbohydrates ?: 0.0, state.goals.carbohydrates, grams)
                    NutrientBar(stringResource(R.string.progress_fat), s.average.fat ?: 0.0, state.goals.fat, grams)
                    NutrientBar(stringResource(R.string.progress_fiber), s.average.fiber ?: 0.0, state.goals.fiber, grams)
                }
                val rows = buildList {
                    s.average.sugars?.let { add(stringResource(R.string.progress_sugars) to it.gramsText()) }
                    s.average.saturatedFat?.let { add(stringResource(R.string.progress_saturated_fat) to it.gramsText()) }
                    s.average.salt?.let { add(stringResource(R.string.progress_salt) to it.gramsText()) }
                    Micronutrient.entries.forEach { m ->
                        s.average.micros[m]?.let { value ->
                            val target = state.goals.micros[m]?.let { " / ${it.gramsText(m.unit.symbol)}" }.orEmpty()
                            add(m.label to value.gramsText(m.unit.symbol) + target)
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rows.forEach { (label, value) ->
                            Row {
                                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (loggingWeight) {
        WeightDialog(state?.latestWeight?.kilograms, onDismiss = { loggingWeight = false }) {
            onLogWeight(it)
            loggingWeight = false
        }
    }
    state?.weights?.firstOrNull { it.id == deleting }?.let { weight ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.progress_delete_weight_title)) },
            text = {
                Text(
                    if (weight.fromHealth) {
                        stringResource(
                            R.string.progress_delete_weight_from_health,
                            weight.kilograms,
                            weight.loggedAt.atZone(zone).toLocalDate().format(formats.shortDate),
                        )
                    } else {
                        stringResource(
                            R.string.progress_delete_weight_local,
                            weight.kilograms,
                            weight.loggedAt.atZone(zone).toLocalDate().format(formats.shortDate),
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteWeight(weight.id)
                    deleting = null
                }) { Text(stringResource(R.string.progress_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.progress_cancel)) } },
        )
    }
}

/** "7 days", "30 days", "90 days". */
@Composable
internal fun ProgressRange.label() = pluralStringResource(R.plurals.progress_range_days, days.toInt(), days.toInt())
