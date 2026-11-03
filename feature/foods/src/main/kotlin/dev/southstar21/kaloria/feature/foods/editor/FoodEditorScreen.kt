package dev.southstar21.kaloria.feature.foods.editor

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.southstar21.kaloria.core.designsystem.component.ContentCard
import dev.southstar21.kaloria.core.designsystem.component.GlassScreen
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.ui.DecimalField
import dev.southstar21.kaloria.core.ui.kcalText
import dev.southstar21.kaloria.feature.foods.FoodEditorKey
import dev.southstar21.kaloria.feature.foods.R

@Composable
fun FoodEditorRoute(
    key: FoodEditorKey,
    onDone: () -> Unit,
    viewModel: FoodEditorViewModel = hiltViewModel<FoodEditorViewModel, FoodEditorViewModel.Factory>(
        key = key.toString(),
        creationCallback = { it.create(key) },
    ),
) {
    val state = viewModel.state
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    FoodEditorScreen(
        state = state,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FoodEditorScreen(
    state: FoodEditorState,
    onEdit: (FoodForm.() -> FoodForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var barPx by remember { mutableIntStateOf(0) }
    val bar = with(LocalDensity.current) { barPx.toDp() }
    val listState = rememberLazyListState()
    val leave = { if (state.changed) confirmDiscard = true else onBack() }
    BackHandler(onBack = leave)
    // A failed save shows what's wrong at the top of the form.
    LaunchedEffect(state.saveAttempts) { if (state.saveAttempts > 0) listState.animateScrollToItem(0) }
    val form = state.form
    val errors = form.errors
    val show = state.showErrors
    val warn = state.scan?.warnings.orEmpty()
    fun flagged(field: String) = warn[field]?.firstOrNull()

    GlassScreen(Modifier.imePadding()) {
        if (state.loading) {
            LoadingIndicator(Modifier.align(Alignment.Center))
            return@GlassScreen
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = bar, bottom = bottom + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (show && errors.any) {
                item(key = "errors") {
                    ContentCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.food_editor_cant_save), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Checks(form, errors, includeRequired = true)
                    }
                }
            }
            state.scan?.let { scan -> item(key = "scan") { ScanCard(scan) } }
            item(key = "identity") {
                ContentCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { v -> onEdit { copy(name = v) } },
                            label = { Text(stringResource(R.string.food_editor_name)) },
                            singleLine = true,
                            isError = show && errors.name,
                            supportingText = if (show && errors.name) ({ Text(stringResource(R.string.food_editor_required)) }) else null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.brand,
                            onValueChange = { v -> onEdit { copy(brand = v) } },
                            label = { Text(stringResource(R.string.food_editor_brand_optional)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.barcode,
                            onValueChange = { v -> onEdit { copy(barcode = v.filter(Char::isDigit)) } },
                            label = { Text(stringResource(R.string.food_editor_barcode_optional)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.food_editor_values_per), style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            AmountUnit.entries.forEachIndexed { i, unit ->
                                SegmentedButton(
                                    selected = form.unit == unit,
                                    onClick = { onEdit { copy(unit = unit) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, AmountUnit.entries.size),
                                ) { Text(stringResource(R.string.food_editor_per_100, unit.symbol)) }
                            }
                        }
                    }
                }
            }

            item(key = "macros") {
                ContentCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.food_editor_nutrition_per_100, form.unit.symbol), style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DecimalField(
                                value = form.energy,
                                onValueChange = { v -> onEdit { copy(energy = v) } },
                                label = stringResource(R.string.food_editor_energy),
                                isError = (show && errors.energy) || (form.energyKcal ?: 0.0) > MAX_KCAL_PER_100 || flagged("energy") != null,
                                supportingText = if (show && errors.energy) stringResource(R.string.food_editor_required) else null,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            SingleChoiceSegmentedButtonRow {
                                EnergyUnit.entries.forEachIndexed { i, unit ->
                                    SegmentedButton(
                                        selected = form.energyUnit == unit,
                                        onClick = { onEdit { copy(energyUnit = unit) } },
                                        shape = SegmentedButtonDefaults.itemShape(i, EnergyUnit.entries.size),
                                    ) { Text(if (unit == EnergyUnit.Kcal) stringResource(R.string.food_editor_kcal) else stringResource(R.string.food_editor_kj)) }
                                }
                            }
                        }
                        if (form.energyUnit == EnergyUnit.Kj && form.energyKcal != null) {
                            Text(
                                stringResource(R.string.food_editor_energy_as_kcal, form.energyKcal.kcalText()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        flagged("energy")?.let { FieldNote(it) }
                        PairRow(
                            { DecimalField(form.protein, { v -> onEdit { copy(protein = v) } }, stringResource(R.string.food_editor_protein), it, suffix = "g", isError = flagged("protein") != null) },
                            { DecimalField(form.fiber, { v -> onEdit { copy(fiber = v) } }, stringResource(R.string.food_editor_fiber), it, suffix = "g", isError = flagged("fiber") != null) },
                        )
                        PairRow(
                            { DecimalField(form.carbohydrates, { v -> onEdit { copy(carbohydrates = v) } }, stringResource(R.string.food_editor_carbs), it, suffix = "g", isError = flagged("carbohydrates") != null) },
                            {
                                DecimalField(
                                    form.sugars,
                                    { v -> onEdit { copy(sugars = v) } },
                                    stringResource(R.string.food_editor_sugars),
                                    it,
                                    suffix = "g",
                                    isError = errors.sugarsOverCarbs || flagged("sugars") != null,
                                )
                            },
                        )
                        PairRow(
                            { DecimalField(form.fat, { v -> onEdit { copy(fat = v) } }, stringResource(R.string.food_editor_fat), it, suffix = "g", isError = flagged("fat") != null) },
                            {
                                DecimalField(
                                    form.saturatedFat,
                                    { v -> onEdit { copy(saturatedFat = v) } },
                                    stringResource(R.string.food_editor_saturated),
                                    it,
                                    suffix = "g",
                                    isError = errors.saturatedOverFat || flagged("saturatedFat") != null,
                                )
                            },
                        )
                        DecimalField(form.salt, { v -> onEdit { copy(salt = v) } }, stringResource(R.string.food_editor_salt), Modifier.fillMaxWidth(), suffix = "g", isError = flagged("salt") != null)
                        listOf("protein", "fiber", "carbohydrates", "sugars", "fat", "saturatedFat", "salt").forEach { field ->
                            warn[field]?.forEach { FieldNote(stringResource(R.string.food_editor_field_note, field.label(), it)) }
                        }
                        Checks(form, errors)
                    }
                }
            }

            item(key = "micros") { MicrosCard(form, onEdit) }
            item(key = "servings") { ServingsCard(form, onEdit) }

            if (state.existing != null) {
                item(key = "delete") {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.DeleteOutline, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.food_editor_delete_food), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Row(
            Modifier
                .onSizeChanged { barPx = it.height }
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .glass()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.food_editor_back)) }
            Text(
                if (state.existing == null) stringResource(R.string.food_editor_new_title) else stringResource(R.string.food_editor_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEdit { copy(favorite = !favorite) } }) {
                Icon(
                    if (form.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    if (form.favorite) stringResource(R.string.food_editor_remove_favorite) else stringResource(R.string.food_editor_add_favorite),
                    tint = if (form.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = bottom + 16.dp)
                .fillMaxWidth()
                .height(56.dp),
        ) { Text(stringResource(R.string.food_editor_save)) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.food_editor_discard_title)) },
            text = { Text(if (state.scan != null) stringResource(R.string.food_editor_discard_scan) else stringResource(R.string.food_editor_discard_edits)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.food_editor_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.food_editor_keep_editing)) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.food_editor_delete_title, form.name)) },
            text = { Text(stringResource(R.string.food_editor_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.food_editor_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.food_editor_cancel)) } },
        )
    }
}
