package dev.southstar21.kaloria.feature.progress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.LineChart
import dev.southstar21.kaloria.core.designsystem.component.NoteChip
import dev.southstar21.kaloria.core.designsystem.component.kgText
import dev.southstar21.kaloria.core.model.HealthAccess
import dev.southstar21.kaloria.core.model.WeightEntry
import dev.southstar21.kaloria.core.ui.DecimalField
import dev.southstar21.kaloria.core.ui.parseDecimal
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
internal fun WeightTile(
    state: ProgressUiState,
    access: HealthAccess?,
    syncing: Boolean,
    onLog: () -> Unit,
    onConnect: () -> Unit,
    onPick: (WeightEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = state.weights
    ContentCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.progress_weight_title), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.latestWeight?.let { "%.1f".format(it.kilograms) } ?: "–",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    )
                    if (state.latestWeight != null) {
                        Text(" kg", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Text(
                    when {
                        points.size >= 2 -> stringResource(R.string.progress_weight_change, points.last().kilograms - points.first().kilograms, state.range.label)
                        state.latestWeight == null -> stringResource(R.string.progress_weight_none)
                        else -> stringResource(R.string.progress_weight_latest)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onLog) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.progress_weight_log))
            }
        }
        if (points.size >= 2) {
            Spacer(Modifier.height(12.dp))
            val (xs, ys) = remember(points) {
                val first = points.first().loggedAt
                val span = ChronoUnit.MINUTES.between(first, points.last().loggedAt).coerceAtLeast(1).toFloat()
                points.map { ChronoUnit.MINUTES.between(first, it.loggedAt) / span } to points.map { it.kilograms }
            }
            LineChart(
                xs = xs,
                ys = ys,
                color = MaterialTheme.colorScheme.primary,
                height = 120.dp,
                valueLabel = { it.kgText() },
                description = stringResource(R.string.progress_weight_chart_description, points.first().kilograms, points.last().kilograms),
            )
        }
        HealthRow(access, syncing, onConnect)
        val recent = points.takeLast(3).reversed()
        if (recent.isNotEmpty()) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { w ->
                    NoteChip(Modifier.fillMaxWidth(), onClick = { onPick(w) }) {
                        Column(Modifier.weight(1f)) {
                            Text(w.kilograms.kgText(), style = MaterialTheme.typography.labelLarge)
                            Text(
                                w.loggedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(ShortDate) +
                                    stringResource(if (w.fromHealth) R.string.progress_weight_from_health else R.string.progress_weight_from_kaloria),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Whether weight is shared with Samsung Health (through Health Connect), and a way to turn it on.
 * It grows in rather than popping, and a sync in progress spins the icon instead of changing the text.
 */
@Composable
private fun HealthRow(access: HealthAccess?, syncing: Boolean, onConnect: () -> Unit) {
    AnimatedVisibility(
        visible = access != null && access != HealthAccess.Unsupported,
        enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(tween(240, delayMillis = 60)),
        exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(tween(140)),
    ) {
        val spin = remember { Animatable(0f) }
        LaunchedEffect(syncing) {
            if (syncing) {
                while (true) spin.animateTo(spin.value + 360f, tween(900, easing = LinearEasing))
            } else {
                // Finish the turn it's on, so the icon doesn't stop at an odd angle.
                spin.animateTo(kotlin.math.ceil(spin.value / 360f) * 360f, spring(dampingRatio = 0.8f, stiffness = 300f))
            }
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Sync,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = -spin.value },
            )
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = access,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                modifier = Modifier.weight(1f),
                label = "health",
            ) { state ->
                Text(
                    when (state) {
                        HealthAccess.Granted -> stringResource(R.string.progress_health_synced)
                        HealthAccess.NeedsPermission -> stringResource(R.string.progress_health_connect_prompt)
                        else -> stringResource(R.string.progress_health_update)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(access == HealthAccess.NeedsPermission, enter = fadeIn(), exit = fadeOut()) {
                FilledTonalButton(onClick = onConnect) { Text(stringResource(R.string.progress_health_connect)) }
            }
        }
    }
}

@Composable
internal fun WeightDialog(previous: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(previous?.let { "%.1f".format(it) }.orEmpty()) }
    val value = text.parseDecimal()?.takeIf { it in 20.0..400.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.progress_weight_dialog_title)) },
        text = { DecimalField(text, { text = it }, stringResource(R.string.progress_weight_field), suffix = "kg", isError = text.isNotEmpty() && value == null) },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(stringResource(R.string.progress_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.progress_cancel)) } },
    )
}
