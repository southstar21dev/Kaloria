package dev.southstar21.kaloria.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.southstar21.kaloria.core.model.PortionUnit

/**
 * A serving being added ([index] null) or edited, with its fields as first shown. [base] is the
 * exact amount in grams or ml behind [amount], kept while the amount isn't edited.
 */
internal data class ServingEdit(val index: Int?, val name: String, val amount: String, val base: Double? = null)

@Composable
internal fun ServingDialog(
    edit: ServingEdit,
    unit: PortionUnit,
    onDismiss: () -> Unit,
    onSave: (name: String, baseAmount: Double) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(edit.name) }
    var amount by rememberSaveable { mutableStateOf(edit.amount) }
    val value = servingAmount(edit, amount, unit)
    val canSave = name.isNotBlank() && value != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (edit.index == null) R.string.serving_dialog_new_title else R.string.serving_dialog_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.serving_dialog_name)) },
                    placeholder = { Text(stringResource(R.string.serving_dialog_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DecimalField(amount, { amount = it }, stringResource(R.string.serving_dialog_amount), Modifier.fillMaxWidth(), suffix = unit.symbol, isError = amount.isNotEmpty() && value == null, imeAction = ImeAction.Done)
            }
        },
        confirmButton = { TextButton(onClick = { value?.let { onSave(name.trim(), it) } }, enabled = canSave) { Text(stringResource(R.string.serving_dialog_save)) } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text(stringResource(R.string.serving_dialog_delete), color = MaterialTheme.colorScheme.tertiary) } }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.serving_dialog_cancel)) }
            }
        },
    )
}
