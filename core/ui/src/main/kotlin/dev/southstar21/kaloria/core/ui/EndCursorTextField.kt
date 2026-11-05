package dev.southstar21.kaloria.core.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

/**
 * A bare single-line field for the big tap-to-edit numbers (the portion amount, goal tiles). Its
 * tile focuses it from anywhere, so the cursor goes to the end when it gains focus, and when the
 * value changes from outside (a serving picked, a unit switched), ready to keep typing or delete.
 */
@Composable
fun EndCursorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // A value from outside shows at once, cursor at the end, without writing state mid-composition;
    // the effect then adopts it as the field's own.
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    }
    BasicTextField(
        value = shown,
        onValueChange = { new ->
            field = new
            if (new.text != value) onValueChange(new.text)
        },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        decorationBox = decorationBox,
        modifier = modifier.onFocusChanged { if (it.isFocused) field = shown.copy(selection = TextRange(shown.text.length)) },
    )
}
