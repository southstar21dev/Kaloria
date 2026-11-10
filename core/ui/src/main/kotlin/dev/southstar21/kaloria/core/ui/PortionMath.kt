package dev.southstar21.kaloria.core.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.PortionUnit
import java.util.Locale

/** The unit amounts are entered in, remembered per kind of food (weighed or poured) across sheets. */
internal class PortionUnits(private val prefs: SharedPreferences, private val base: AmountUnit) {
    val options = PortionUnit.forBase(base)
    var current by mutableStateOf(options.firstOrNull { it.name == prefs.getString(base.name, null) } ?: PortionUnit.default(base))
        private set

    fun choose(unit: PortionUnit) {
        current = unit
        prefs.edit { putString(base.name, unit.name) }
    }
}

@Composable
internal fun rememberPortionUnits(base: AmountUnit): PortionUnits {
    val context = LocalContext.current
    return remember(base) { PortionUnits(context.getSharedPreferences("portion_units", Context.MODE_PRIVATE), base) }
}

/** An amount in grams or ml as this unit shows it: "0.33", "11.16", "45", never "45.0". */
internal fun PortionUnit.format(base: Double): String {
    val v = fromBase(base)
    val text = "%.${decimals}f".format(Locale.ROOT, v)
    return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
}

/** The most one portion can be, in grams or ml: 10 kg or 10 l is past any single meal. */
const val MAX_PORTION = 10_000.0

/**
 * What [text] in [unit] stands for in grams or ml. While the text still reads as [exact] does, it
 * is [exact] itself, so rounding the display (11.16 fl oz) never changes what's logged (330 ml).
 */
internal fun portionAmount(text: String, exact: Double, unit: PortionUnit): Double? {
    val typed = text.parseDecimal() ?: return null
    return if (unit.format(exact) == text) exact else unit.toBase(typed)
}

/** Whether [amount] can be logged: more than nothing, and no more than [MAX_PORTION]. */
fun isValidPortion(amount: Double?): Boolean = amount != null && amount > 0 && amount <= MAX_PORTION

/**
 * The exact amount and the field's text after switching from [from] to [to]. With nothing valid
 * typed the field stays as it is, rather than bringing back an amount the user had cleared.
 */
internal fun switchUnit(text: String, exact: Double, from: PortionUnit, to: PortionUnit): Pair<Double, String> {
    val amount = portionAmount(text, exact, from) ?: return exact to text
    return amount to to.format(amount)
}

/**
 * The amount in grams or ml a serving dialog saves. While the amount text is the one it opened
 * with, that's the serving's exact amount, so renaming a 28 g serving shown as "0.99" oz keeps
 * 28 g rather than storing 28.07 g.
 */
internal fun servingAmount(edit: ServingEdit, text: String, unit: PortionUnit): Double? {
    if (text == edit.amount && edit.base != null) return edit.base
    return text.parseDecimal()?.let(unit::toBase)?.takeIf(::isValidPortion)
}
