package dev.southstar21.kaloria.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.southstar21.kaloria.core.designsystem.component.BarChart
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.LineChart
import dev.southstar21.kaloria.core.designsystem.component.LocalDockPadding
import dev.southstar21.kaloria.core.designsystem.component.NutrientBar
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.designsystem.component.introItem
import dev.southstar21.kaloria.core.designsystem.component.rememberIntro
import dev.southstar21.kaloria.core.designsystem.theme.MacroColors
import dev.southstar21.kaloria.core.designsystem.theme.TrackerColors
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.ui.DecimalField
import dev.southstar21.kaloria.core.ui.gramsText
import dev.southstar21.kaloria.core.ui.kcalText
import dev.southstar21.kaloria.core.ui.parseDecimal
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSteps()
        onPauseOrDispose { }
    }
    ProgressScreen(state, viewModel.steps, viewModel::setRange, viewModel::logWeight)
}

@Composable
internal fun ProgressScreen(
    state: ProgressUiState?,
    steps: List<Long?>?,
    onRange: (ProgressRange) -> Unit,
    onLogWeight: (Double) -> Unit,
) {
    val dock = LocalDockPadding.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var loggingWeight by rememberSaveable { mutableStateOf(false) }
    val intro = rememberIntro()

    LazyColumn(
        modifier = Modifier.fillMaxSize().glassSource(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = dock + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Progress", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ProgressRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = state?.range == r,
                        onClick = { onRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, ProgressRange.entries.size),
                    ) { Text(r.label) }
                }
            }
        }
        if (state == null) return@LazyColumn
        val s = state.summary
        val labels = axisLabels(s.days)

        item(key = "energy") {
            GlassCard(Modifier.introItem(intro, 0).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Energy", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${s.loggedDays} of ${s.days.size} days logged",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(s.average.energyKcal.kcalText(), style = MaterialTheme.typography.headlineSmall)
                            Text("kcal / logged day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    BarChart(
                        values = s.energy,
                        goal = state.goals.energyKcal?.toDouble(),
                        labels = labels,
                        description = "Energy per day, average ${s.average.energyKcal.kcalText()} kcal",
                    )
                }
            }
        }

        item(key = "macros") {
            GlassCard(Modifier.introItem(intro, 1).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Average per logged day", style = MaterialTheme.typography.titleSmall)
                    NutrientBar("Protein", s.average.protein ?: 0.0, state.goals.protein, "g", MacroColors.Protein)
                    NutrientBar("Carbs", s.average.carbohydrates ?: 0.0, state.goals.carbohydrates, "g", MacroColors.Carbs)
                    NutrientBar("Fat", s.average.fat ?: 0.0, state.goals.fat, "g", MacroColors.Fat)
                    NutrientBar("Fiber", s.average.fiber ?: 0.0, state.goals.fiber, "g", MacroColors.Fiber)
                }
            }
        }

        item(key = "weight") {
            GlassCard(Modifier.introItem(intro, 2).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Weight", style = MaterialTheme.typography.titleSmall)
                            Text(
                                state.latestWeight?.let { "%.1f kg".format(it.kilograms) } ?: "No weigh-ins yet",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        FilledTonalButton(onClick = { loggingWeight = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Log weight")
                        }
                    }
                    val points = state.weights
                    if (points.size >= 2) {
                        val first = points.first().loggedAt
                        val span = ChronoUnit.MINUTES.between(first, points.last().loggedAt).coerceAtLeast(1).toFloat()
                        val change = points.last().kilograms - points.first().kilograms
                        LineChart(
                            xs = points.map { ChronoUnit.MINUTES.between(first, it.loggedAt) / span },
                            ys = points.map { it.kilograms },
                            valueLabel = { "%.1f kg".format(it) },
                            description = "Weight from %.1f to %.1f kg".format(points.first().kilograms, points.last().kilograms),
                        )
                        Text(
                            "%+.1f kg over %d weigh-ins".format(change, points.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (steps != null) {
            item(key = "steps") {
                GlassCard(Modifier.introItem(intro, 3).fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val counted = steps.filterNotNull()
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("Steps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                if (counted.isEmpty()) "–" else "%,d / day".format(counted.average().toLong()),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        BarChart(
                            values = steps.map { it?.toDouble() },
                            goal = state.goals.steps?.toDouble(),
                            color = TrackerColors.Steps,
                            overColor = TrackerColors.Steps,
                            labels = labels,
                            description = "Steps per day",
                        )
                    }
                }
            }
        }

        item(key = "micros") {
            GlassCard(Modifier.introItem(intro, 4).fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Micronutrients, average per logged day", style = MaterialTheme.typography.titleSmall)
                    val rows = buildList {
                        s.average.sugars?.let { add("Sugars" to it.gramsText()) }
                        s.average.saturatedFat?.let { add("Saturated fat" to it.gramsText()) }
                        s.average.salt?.let { add("Salt" to it.gramsText()) }
                        Micronutrient.entries.forEach { m ->
                            s.average.micros[m]?.let { value ->
                                val target = state.goals.micros[m]?.let { " / ${it.gramsText(m.unit.symbol)}" }.orEmpty()
                                add(m.label to value.gramsText(m.unit.symbol) + target)
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            "No micronutrient data in this range. Scanned labels and USDA foods carry the most.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

    if (loggingWeight) WeightDialog(state?.latestWeight?.kilograms, onDismiss = { loggingWeight = false }) {
        onLogWeight(it)
        loggingWeight = false
    }
}

@Composable
private fun WeightDialog(previous: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(previous?.let { "%.1f".format(it) }.orEmpty()) }
    val value = text.parseDecimal()?.takeIf { it in 20.0..400.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = { DecimalField(text, { text = it }, "Weight", suffix = "kg", isError = text.isNotEmpty() && value == null) },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Few enough labels to stay legible: every day for a week, then about five, ending on today. */
private fun axisLabels(days: List<java.time.LocalDate>): List<String> {
    if (days.size <= 7) return days.map { it.format(DateTimeFormatter.ofPattern("EEEEE")) }
    val step = (days.size - 1) / 4
    return days.indices.map { i -> if ((days.lastIndex - i) % step == 0) days[i].format(DateTimeFormatter.ofPattern("d MMM")) else "" }
}

