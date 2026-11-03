package dev.southstar21.kaloria.core.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
        prefs.edit().putString(base.name, unit.name).apply()
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
