package dev.southstar21.kaloria.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.component.GlassDefaults
import dev.southstar21.kaloria.core.designsystem.component.frostedGlass
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.PortionUnit
import dev.southstar21.kaloria.core.model.Serving
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * Bottom sheet for choosing how much of a food was eaten and when. Used both to log a new entry
 * and to edit an existing one (pass [onDelete]). A pane of the app's frosted glass: the amount is
 * a big number typed straight in, in whichever unit suits (0.33 l rather than 330 ml), servings
 * are pills, and the portion's nutrition updates live. Amounts reach [onConfirm] in [unit].
 *
 * @param onServingsChange when the food's servings can be saved (it's in the user's foods): adds
 *   a "+ Serving" pill, and a long press on a serving renames or deletes it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PortionSheet(
    title: String,
    subtitle: String?,
    unit: AmountUnit,
    per100: Nutrients,
    servings: List<Serving>,
    initialAmount: Double?,
    initialTime: LocalTime,
    confirmLabel: String,
    onConfirm: (amount: Double, time: LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onServingsChange: ((List<Serving>) -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val units = rememberPortionUnits(unit)
    // Keyed, so servings that arrive after the sheet opens replace the empty list instead of being
    // ignored, and an addition is never saved over servings the sheet didn't show.
    var shown by remember(servings) { mutableStateOf(servings) }
    // The amount in grams or ml, kept exact when it comes from a serving or a unit switch, so
    // rounding the display (11.16 fl oz) never changes what's logged (330 ml).
    var exact by rememberSaveable { mutableDoubleStateOf(initialAmount ?: servings.firstOrNull()?.amount ?: 100.0) }
    var amountText by rememberSaveable { mutableStateOf(units.current.format(exact)) }
    var time by rememberSaveable { mutableStateOf(initialTime) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServingEdit?>(null) }
    val amount = portionAmount(amountText, exact, units.current)
    val portion = amount?.let(per100::scaledTo)
    val valid = isValidPortion(amount)
    val scope = rememberCoroutineScope()
    // Set once a button starts closing the sheet, so a second tap while it slides away does nothing.
    var closing by remember { mutableStateOf(false) }

    // Slides the sheet away before [action] lets the caller remove it, rather than it vanishing.
    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) action() else closing = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The sheet draws its own glass pane, handle included.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Slightly more white than a card, so it reads over the camera too.
                .frostedGlass(SheetShape, tint = Color.White.copy(alpha = 0.6f), iridescent = true)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Pill(onClick = { pickingTime = true }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(time.timeText(), style = MaterialTheme.typography.labelLarge)
                }
                if (onDelete != null) {
                    IconButton(onClick = { closeThen(onDelete) }, modifier = Modifier.size(40.dp).frostedGlass(CircleShape)) {
                        Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.portion_sheet_delete_entry), Modifier.size(20.dp))
                    }
                }
            }

            AmountTile(
                value = amountText,
                onValueChange = { amountText = it },
                current = units.current,
                options = units.options,
                onUnit = { new ->
                    val (switched, text) = switchUnit(amountText, exact, units.current, new)
                    units.choose(new)
                    exact = switched
                    amountText = text
                },
                valid = valid,
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEachIndexed { index, serving ->
                    Pill(
                        selected = amount != null && kotlin.math.abs(amount - serving.amount) < 0.01,
                        selectable = true,
                        onClick = {
                            exact = serving.amount
                            amountText = units.current.format(serving.amount)
                        },
                        onLongClick = onServingsChange?.let { { editing = ServingEdit(index, serving.name, units.current.format(serving.amount), serving.amount) } },
                        onLongClickLabel = stringResource(R.string.portion_sheet_edit_serving),
                    ) {
                        Text(stringResource(R.string.portion_sheet_serving_pill, serving.name, units.current.format(serving.amount), units.current.symbol), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (onServingsChange != null) {
                    Pill(outlined = true, onClick = { editing = ServingEdit(null, "", amountText, amount) }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.portion_sheet_add_serving), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().frostedGlass(GlassDefaults.Shape).padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PortionStat(stringResource(R.string.portion_sheet_kcal), portion?.energyKcal.kcalText(), MaterialTheme.colorScheme.primary)
                PortionStat(stringResource(R.string.portion_sheet_protein), portion?.protein.gramsText(), MaterialTheme.colorScheme.onSurface)
                PortionStat(stringResource(R.string.portion_sheet_carbs), portion?.carbohydrates.gramsText(), MaterialTheme.colorScheme.onSurface)
                PortionStat(stringResource(R.string.portion_sheet_fat), portion?.fat.gramsText(), MaterialTheme.colorScheme.onSurface)
            }

            Button(
                onClick = { amount?.let { confirmed -> closeThen { onConfirm(confirmed, time) } } },
                enabled = valid,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(confirmLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    editing?.let { edit ->
        ServingDialog(
            edit = edit,
            unit = units.current,
            onDismiss = { editing = null },
            onSave = { name, base ->
                val serving = Serving(name, base)
                shown = if (edit.index == null) shown + serving else shown.mapIndexed { i, s -> if (i == edit.index) serving else s }
                onServingsChange?.invoke(shown)
                editing = null
            },
            onDelete = edit.index?.let { index ->
                {
                    shown = shown.filterIndexed { i, _ -> i != index }
                    onServingsChange?.invoke(shown)
                    editing = null
                }
            },
        )
    }

    if (pickingTime) {
        val state = rememberTimePickerState(time.hour, time.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickingTime = false
                }) { Text(stringResource(R.string.portion_sheet_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.portion_sheet_cancel)) } },
            title = { Text(stringResource(R.string.portion_sheet_time_eaten)) },
            text = { TimePicker(state) },
        )
    }
}

private val SheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/** The amount as a big number typed straight into a frosted tile, with its unit picked beside it. */
@Composable
private fun AmountTile(
    value: String,
    onValueChange: (String) -> Unit,
    current: PortionUnit,
    options: List<PortionUnit>,
    onUnit: (PortionUnit) -> Unit,
    valid: Boolean,
) {
    val focus = remember { FocusRequester() }
    val color = if (valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary
    val style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, color = color)
    Column(
        Modifier
            .fillMaxWidth()
            .frostedGlass(GlassDefaults.Shape)
            .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.portion_sheet_amount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                options.forEach { option ->
                    val selected = option == current
                    val background by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "unit",
                    )
                    Text(
                        option.symbol,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            // A full-size touch target around the small chip.
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(background)
                            .selectable(selected = selected, role = Role.RadioButton) { onUnit(option) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            EndCursorTextField(
                value = value,
                onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' || it == ',' }.take(8)) },
                textStyle = style,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.focusRequester(focus).width(IntrinsicSize.Max),
            )
            Text(
                " ${current.symbol}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * A small white pill: servings, the time, "+ Serving". [selected] fills it with ink; [outlined] is
 * the quieter look for adding something. [selectable] pills also tell accessibility services
 * whether they're selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pill(
    onClick: () -> Unit,
    selected: Boolean = false,
    selectable: Boolean = false,
    outlined: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val background by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            outlined -> Color.Transparent
            else -> Color.White.copy(alpha = 0.85f)
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pill",
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            Modifier
                .minimumInteractiveComponentSize()
                .then(if (selectable) Modifier.semantics { this.selected = selected } else Modifier)
                .clip(CircleShape)
                .background(background)
                .then(if (outlined) Modifier.border(1.dp, Color.White, CircleShape) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel.takeIf { onLongClick != null })
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun PortionStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
