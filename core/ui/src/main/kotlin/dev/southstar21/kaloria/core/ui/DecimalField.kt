package dev.southstar21.kaloria.core.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** A numeric text field that accepts "12", "12.5" and "12,5". */
@Composable
fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    integerOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val allowed = if (integerOnly) input.filter(Char::isDigit) else input.filter { it.isDigit() || it == '.' || it == ',' }
            onValueChange(allowed)
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        modifier = modifier,
    )
}
