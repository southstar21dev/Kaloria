package dev.southstar21.kaloria.feature.foods.editor

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.southstar21.kaloria.core.designsystem.component.GlassCard
import dev.southstar21.kaloria.core.designsystem.component.GlassScreen
import dev.southstar21.kaloria.core.designsystem.component.glass
import dev.southstar21.kaloria.core.designsystem.component.glassSource
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.ui.DecimalField
import dev.southstar21.kaloria.core.ui.kcalText
import dev.southstar21.kaloria.core.scanner.label.ExtractionMethod
import dev.southstar21.kaloria.feature.foods.FoodEditorKey
import kotlin.math.roundToInt

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
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text("Can't save yet", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(4.dp))
                        Checks(form, errors, includeRequired = true)
                    }
                }
            }
            state.scan?.let { scan -> item(key = "scan") { ScanCard(scan) } }
            item(key = "identity") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.name,
                            onValueChange = { v -> onEdit { copy(name = v) } },
                            label = { Text("Name") },
                            singleLine = true,
                            isError = show && errors.name,
                            supportingText = if (show && errors.name) ({ Text("Required") }) else null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.brand,
                            onValueChange = { v -> onEdit { copy(brand = v) } },
                            label = { Text("Brand (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = form.barcode,
                            onValueChange = { v -> onEdit { copy(barcode = v.filter(Char::isDigit)) } },
                            label = { Text("Barcode (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Values are per", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            AmountUnit.entries.forEachIndexed { i, unit ->
                                SegmentedButton(
                                    selected = form.unit == unit,
                                    onClick = { onEdit { copy(unit = unit) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, AmountUnit.entries.size),
                                ) { Text("100 ${unit.symbol}") }
                            }
                        }
                    }
                }
            }

            item(key = "macros") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nutrition per 100 ${form.unit.symbol}", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DecimalField(
                                value = form.energy,
                                onValueChange = { v -> onEdit { copy(energy = v) } },
                                label = "Energy",
                                isError = (show && errors.energy) || (form.energyKcal ?: 0.0) > MAX_KCAL_PER_100 || flagged("energy") != null,
                                supportingText = if (show && errors.energy) "Required" else null,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            SingleChoiceSegmentedButtonRow {
                                EnergyUnit.entries.forEachIndexed { i, unit ->
                                    SegmentedButton(
                                        selected = form.energyUnit == unit,
                                        onClick = { onEdit { copy(energyUnit = unit) } },
                                        shape = SegmentedButtonDefaults.itemShape(i, EnergyUnit.entries.size),
                                    ) { Text(if (unit == EnergyUnit.Kcal) "kcal" else "kJ") }
                                }
                            }
                        }
                        if (form.energyUnit == EnergyUnit.Kj && form.energyKcal != null) {
                            Text(
                                "= ${form.energyKcal.kcalText()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        flagged("energy")?.let { FieldNote(it) }
                        PairRow(
                            { DecimalField(form.protein, { v -> onEdit { copy(protein = v) } }, "Protein", it, suffix = "g", isError = flagged("protein") != null) },
                            { DecimalField(form.fiber, { v -> onEdit { copy(fiber = v) } }, "Fiber", it, suffix = "g", isError = flagged("fiber") != null) },
                        )
                        PairRow(
                            { DecimalField(form.carbohydrates, { v -> onEdit { copy(carbohydrates = v) } }, "Carbs", it, suffix = "g", isError = flagged("carbohydrates") != null) },
                            {
                                DecimalField(
                                    form.sugars, { v -> onEdit { copy(sugars = v) } }, "Sugars", it, suffix = "g",
                                    isError = errors.sugarsOverCarbs || flagged("sugars") != null,
                                )
                            },
                        )
                        PairRow(
                            { DecimalField(form.fat, { v -> onEdit { copy(fat = v) } }, "Fat", it, suffix = "g", isError = flagged("fat") != null) },
                            {
                                DecimalField(
                                    form.saturatedFat, { v -> onEdit { copy(saturatedFat = v) } }, "Saturated", it,
                                    suffix = "g", isError = errors.saturatedOverFat || flagged("saturatedFat") != null,
                                )
                            },
                        )
                        DecimalField(form.salt, { v -> onEdit { copy(salt = v) } }, "Salt", Modifier.fillMaxWidth(), suffix = "g", isError = flagged("salt") != null)
                        listOf("protein", "fiber", "carbohydrates", "sugars", "fat", "saturatedFat", "salt").forEach { field ->
                            warn[field]?.forEach { FieldNote("${field.label()}: $it") }
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
                        Text("Delete food", color = MaterialTheme.colorScheme.error)
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
            IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(
                if (state.existing == null) "New food" else "Edit food",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onEdit { copy(favorite = !favorite) } }) {
                Icon(
                    if (form.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    if (form.favorite) "Remove from favorites" else "Add to favorites",
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
        ) { Text("Save food") }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text(if (state.scan != null) "The values read from the label will be lost." else "Your edits to this food won't be saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${form.name}?") },
            text = { Text("Logged entries keep their values; the food disappears from search.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PairRow(start: @Composable (Modifier) -> Unit, end: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        start(Modifier.weight(1f))
        end(Modifier.weight(1f))
    }
}

@Composable
private fun Checks(form: FoodForm, errors: FormErrors, includeRequired: Boolean = false) {
    val messages = buildList {
        if (includeRequired && errors.name) add("A name is required.")
        if (includeRequired && errors.energy) add("Energy is required.")
        if (errors.sugarsOverCarbs) add("Sugars can't be more than carbs.")
        if (errors.saturatedOverFat) add("Saturated fat can't be more than fat.")
        if (errors.macrosOver100) add("Macros add up to more than 100 g per 100 g.")
        if (errors.implausible) add("A value is impossible per 100 ${form.unit.symbol} (energy over ${MAX_KCAL_PER_100.toInt()} kcal or a nutrient over 100 g).")
        if (errors.unparseable) add("Some values aren't numbers.")
        form.energyMismatchKcal?.let { diff ->
            add(
                "The macros imply ${form.nutrients().atwaterKcal.kcalText()} kcal, " +
                    "${kotlin.math.abs(diff).roundToInt()} kcal ${if (diff > 0) "more" else "less"} than stated. " +
                    "Double-check against the label.",
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
private fun MicrosCard(form: FoodForm, onEdit: (FoodForm.() -> FoodForm) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Micronutrients", style = MaterialTheme.typography.titleSmall)
            form.micros.forEach { (m, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DecimalField(
                        value = text,
                        onValueChange = { v -> onEdit { copy(micros = micros + (m to v)) } },
                        label = m.label,
                        suffix = m.unit.symbol,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEdit { copy(micros = micros - m) } }) { Icon(Icons.Rounded.Close, "Remove ${m.label}") }
                }
            }
            Box {
                TextButton(onClick = { picking = true }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add micronutrient")
                }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    Micronutrient.entries.filter { it !in form.micros }.forEach { m ->
                        DropdownMenuItem(
                            text = { Text("${m.label} (${m.unit.symbol})") },
                            onClick = { picking = false; onEdit { copy(micros = micros + (m to "")) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServingsCard(form: FoodForm, onEdit: (FoodForm.() -> FoodForm) -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Servings", style = MaterialTheme.typography.titleSmall)
            Text(
                "Named portions for quick logging, e.g. “1 slice” = 28 ${form.unit.symbol}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            form.servings.forEachIndexed { index, serving ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = serving.name,
                        onValueChange = { v -> onEdit { copy(servings = servings.replace(index, serving.copy(name = v))) } },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                    )
                    DecimalField(
                        value = serving.amount,
                        onValueChange = { v -> onEdit { copy(servings = servings.replace(index, serving.copy(amount = v))) } },
                        label = "Amount",
                        suffix = form.unit.symbol,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onEdit { copy(servings = servings.filterIndexed { i, _ -> i != index }) } }) {
                        Icon(Icons.Rounded.Close, "Remove serving")
                    }
                }
            }
            TextButton(onClick = { onEdit { copy(servings = servings + ServingInput()) } }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add serving")
            }
        }
    }
}

private fun <T> List<T>.replace(index: Int, value: T) = toMutableList().also { it[index] = value }

private fun String.label() = when (this) {
    "saturatedFat" -> "Saturated fat"
    "carbohydrates" -> "Carbs"
    else -> replaceFirstChar { it.uppercase() }
}

@Composable
private fun FieldNote(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** The label photo next to the form, so every value can be checked against the original. */
@Composable
private fun ScanCard(scan: ScanReview) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val bitmap = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, scan.photo) {
        value = scan.photo?.let { file ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
            }
        }
    }.value
    GlassCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (scan.method == ExtractionMethod.GeminiNano) Icons.Rounded.AutoAwesome else Icons.Rounded.TextFields,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (scan.method == ExtractionMethod.GeminiNano) "Read by Gemini Nano" else "Read by text rules",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "Smaller" else "Enlarge", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                if (scan.warnings.isEmpty()) "Check every value against the photo before saving."
                else "Check every value against the photo. Highlighted fields need a closer look.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            bitmap?.let {
                androidx.compose.foundation.Image(
                    bitmap = it,
                    contentDescription = "Photo of the nutrition label",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (expanded) 520.dp else 220.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}
