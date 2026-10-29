package dev.southstar21.kaloria.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.designsystem.theme.MacroColors
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.Serving
import java.time.LocalTime

/**
 * Bottom sheet for choosing how much of a food was eaten and when. Used both to log a new entry
 * and to edit an existing one (pass [onDelete]).
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
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var amountText by rememberSaveable { mutableStateOf(initialAmount?.toInputText() ?: servings.firstOrNull()?.amount?.toInputText() ?: "100") }
    var time by rememberSaveable { mutableStateOf(initialTime) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val amount = amountText.parseDecimal()
    val portion = amount?.let(per100::scaledTo)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete entry") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DecimalField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Amount",
                    suffix = unit.symbol,
                    isError = amount == null || amount <= 0,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                AssistChip(
                    onClick = { pickingTime = true },
                    label = { Text(time.timeText()) },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                )
            }

            if (servings.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    servings.forEach { serving ->
                        FilterChip(
                            selected = amount == serving.amount,
                            onClick = { amountText = serving.amount.toInputText() },
                            label = { Text("${serving.name} · ${serving.amount.toInputText()} ${unit.symbol}") },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                PortionStat("kcal", portion?.energyKcal.kcalText(), MaterialTheme.colorScheme.primary)
                PortionStat("Protein", portion?.protein.gramsText(), MacroColors.Protein)
                PortionStat("Carbs", portion?.carbohydrates.gramsText(), MacroColors.Carbs)
                PortionStat("Fat", portion?.fat.gramsText(), MacroColors.Fat)
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { amount?.let { onConfirm(it, time) } },
                enabled = amount != null && amount > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(confirmLabel)
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(time.hour, time.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
            title = { Text("Time eaten") },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun PortionStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
