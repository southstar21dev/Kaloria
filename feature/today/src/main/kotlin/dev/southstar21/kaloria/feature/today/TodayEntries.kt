package dev.southstar21.kaloria.feature.today

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.NoteChip
import dev.southstar21.kaloria.core.model.LogEntry
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.ui.gramsText
import dev.southstar21.kaloria.core.ui.gramsUnit
import dev.southstar21.kaloria.core.ui.kcalText
import dev.southstar21.kaloria.core.ui.nameRes
import dev.southstar21.kaloria.core.ui.timeText
import kotlin.math.roundToInt

/** The day's entries as white notes; tap one to change or delete it. */
@Composable
internal fun LoggedTile(state: TodayUiState, onEdit: (LogEntry) -> Unit, modifier: Modifier = Modifier) {
    ContentCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (state.date == state.today) R.string.today_logged_today else R.string.today_logged), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (state.entries.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.today_entries, state.entries.size, state.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.entries.isEmpty() && !state.loading) {
            Text(
                stringResource(R.string.today_logged_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Keyed and in their own skippable composable: editing one entry redraws only that chip.
            // They share one card, so they stay in the card's column rather than being lazy items.
            state.entries.forEach { entry ->
                key(entry.id) { EntryChip(entry, onEdit) }
            }
        }
    }
}

@Composable
private fun EntryChip(entry: LogEntry, onEdit: (LogEntry) -> Unit) {
    val n = entry.nutrients
    NoteChip(Modifier.fillMaxWidth(), onClick = { onEdit(entry) }) {
        Column(Modifier.weight(1f)) {
            Text(entry.foodName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    R.string.today_entry_details,
                    entry.loggedAt.timeText(),
                    entry.amount.roundToInt(),
                    entry.unit.symbol,
                    n.protein.gramsText(),
                    n.carbohydrates.gramsText(),
                    n.fat.gramsText(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(n.energyKcal.kcalText(), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun MicroCard(totals: Nutrients, targets: Map<Micronutrient, Double>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val sugars = stringResource(R.string.today_micro_sugars)
    val saturatedFat = stringResource(R.string.today_micro_saturated_fat)
    val salt = stringResource(R.string.today_micro_salt)
    val grams = gramsUnit()
    val resources = LocalResources.current
    val rows = remember(totals, targets, sugars, saturatedFat, salt, grams, resources) {
        microRows(totals, targets, MicroLabels(sugars, saturatedFat, salt, grams, resources))
    }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = 0.6f), label = "chevron")
    val state = stringResource(if (expanded) R.string.today_expanded else R.string.today_collapsed)
    ContentCard(modifier.fillMaxWidth().semantics { stateDescription = state }, onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.today_micronutrients_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.today_micronutrients_tracked, rows.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.Rounded.ExpandMore, null, Modifier.graphicsLayer { rotationZ = chevron })
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (rows.isEmpty()) {
                    Text(
                        stringResource(R.string.today_micronutrients_empty),
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

/** The words [microRows] needs, read from resources by the composable that calls it. */
private class MicroLabels(val sugars: String, val saturatedFat: String, val salt: String, val grams: String, val resources: Resources)

/** Label, value text and raw value of each tracked micronutrient (plus sugars, saturated fat, salt). */
private fun microRows(totals: Nutrients, targets: Map<Micronutrient, Double>, labels: MicroLabels) = buildList {
    totals.sugars?.let { add(Triple(labels.sugars, it.gramsText(labels.grams), null)) }
    totals.saturatedFat?.let { add(Triple(labels.saturatedFat, it.gramsText(labels.grams), null)) }
    totals.salt?.let { add(Triple(labels.salt, it.gramsText(labels.grams), null)) }
    Micronutrient.entries.forEach { m ->
        // Sodium includes what salt-only foods contribute; the total resolves it per entry.
        val value = if (m == Micronutrient.Sodium) totals.sodiumMg else totals.micros[m]
        if (value != null || targets[m] != null) {
            val amount = (value ?: 0.0).gramsText(m.unit.symbol)
            val text = targets[m]?.let { labels.resources.getString(R.string.today_micro_of_target, amount, it.gramsText(m.unit.symbol)) } ?: amount
            add(Triple(labels.resources.getString(m.nameRes), text, value))
        }
    }
}
