package dev.southstar21.kaloria.feature.foods.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.ui.DecimalField
import dev.southstar21.kaloria.core.ui.displayName
import dev.southstar21.kaloria.core.ui.kcalText
import dev.southstar21.kaloria.feature.foods.R
import kotlin.math.roundToInt

@Composable
internal fun PairRow(start: @Composable (Modifier) -> Unit, end: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        start(Modifier.weight(1f))
        end(Modifier.weight(1f))
    }
}

@Composable
internal fun Checks(form: FoodForm, errors: FormErrors, includeRequired: Boolean = false) {
    val mismatch = remember(form) { form.energyMismatchKcal?.let { it to form.nutrients().atwaterKcal } }
    val messages = buildList {
        listOf(
            (includeRequired && errors.name) to R.string.food_editor_check_name,
            errors.tooLong to R.string.food_editor_check_too_long,
            (includeRequired && errors.energy) to R.string.food_editor_check_energy,
            errors.sugarsOverCarbs to R.string.food_editor_check_sugars,
            errors.saturatedOverFat to R.string.food_editor_check_saturated,
            errors.macrosOver100 to R.string.food_editor_check_macros_over,
        ).forEach { (failed, message) -> if (failed) add(stringResource(message)) }
        if (errors.implausible) add(stringResource(R.string.food_editor_check_implausible, form.unit.symbol, MAX_KCAL_PER_100.toInt()))
        if (errors.unparseable) add(stringResource(R.string.food_editor_check_unparseable))
        if (errors.servings) add(stringResource(R.string.food_editor_check_servings))
        mismatch?.let { (diff, implied) ->
            add(
                stringResource(
                    if (diff > 0) R.string.food_editor_check_mismatch_more else R.string.food_editor_check_mismatch_less,
                    implied.kcalText(),
                    kotlin.math.abs(diff).roundToInt(),
                ),
            )
        }
    }
    messages.forEach { message ->
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun MicrosCard(form: FoodForm, onEdit: (FoodForm.() -> FoodForm) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    ContentCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.food_editor_micronutrients), style = MaterialTheme.typography.titleSmall)
            form.micros.forEach { (m, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecimalField(
                        value = text,
                        onValueChange = { v -> onEdit { copy(micros = micros + (m to v)) } },
                        label = m.displayName(),
                        suffix = m.unit.symbol,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEdit { copy(micros = micros - m) } }) { Icon(Icons.Rounded.Close, stringResource(R.string.food_editor_remove_micro, m.displayName())) }
                }
            }
            Box {
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.food_editor_add_micro))
                }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    Micronutrient.entries.filter { it !in form.micros }.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.food_editor_micro_option, m.displayName(), m.unit.symbol)) },
                            onClick = {
                                picking = false
                                onEdit { copy(micros = micros + (m to "")) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ServingsCard(form: FoodForm, onEdit: (FoodForm.() -> FoodForm) -> Unit) {
    ContentCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.food_editor_servings), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.food_editor_servings_hint, form.unit.symbol),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.servings.forEachIndexed { index, serving ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = serving.name,
                        onValueChange = { v -> onEdit { copy(servings = servings.replace(index, serving.copy(name = v))) } },
                        label = { Text(stringResource(R.string.food_editor_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                    )
                    DecimalField(
                        value = serving.amount,
                        onValueChange = { v -> onEdit { copy(servings = servings.replace(index, serving.copy(amount = v))) } },
                        label = stringResource(R.string.food_editor_amount),
                        suffix = form.unit.symbol,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEdit { copy(servings = servings.filterIndexed { i, _ -> i != index }) } }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.food_editor_remove_serving))
                    }
                }
            }
            TextButton(onClick = { onEdit { copy(servings = servings + ServingInput()) } }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.food_editor_add_serving))
            }
        }
    }
}

private fun <T> List<T>.replace(index: Int, value: T) = toMutableList().also { it[index] = value }

/** The name shown for a nutrient field of the form. */
@Composable
internal fun String.label() = when (this) {
    "energy" -> stringResource(R.string.food_editor_energy)
    "protein" -> stringResource(R.string.food_editor_protein)
    "fiber" -> stringResource(R.string.food_editor_fiber)
    "carbohydrates" -> stringResource(R.string.food_editor_carbs)
    "sugars" -> stringResource(R.string.food_editor_sugars)
    "fat" -> stringResource(R.string.food_editor_fat)
    "saturatedFat" -> stringResource(R.string.food_editor_saturated_fat)
    "salt" -> stringResource(R.string.food_editor_salt)
    else -> replaceFirstChar { it.uppercase() }
}

@Composable
internal fun FieldNote(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}
