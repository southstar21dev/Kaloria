package dev.southstar21.kaloria.core.designsystem.component

import java.text.NumberFormat

/** Whole numbers with the user's thousands separator (2,200 / 2 200 / 2.200), for display. */
fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

fun Long.grouped(): String = NumberFormat.getIntegerInstance().format(this)

/** A weight in kilograms to one decimal, in the user's locale: "82.4 kg". */
fun Double.kgText(): String = "%.1f kg".format(this)
