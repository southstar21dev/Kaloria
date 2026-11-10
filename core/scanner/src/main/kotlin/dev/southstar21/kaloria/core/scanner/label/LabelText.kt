package dev.southstar21.kaloria.core.scanner.label

import java.text.Normalizer
import java.util.Locale

// Compiled once: rows are normalised for every frame the live label search reads.
private val combiningMarks = Regex("""\p{Mn}+""")
private val letterO = Regex("""(?<=[\d,.])[oO]|[oO](?=[\d,.]\d)""")
private val letterL = Regex("""(?<=\d)[lI]|[lI](?=\d)""")
private val spaces = Regex("""\s+""")

/** Words that may precede a sub-row: "ebből", "of which", "bunlardan", "o cümlədən", "-". */
internal val subRowPrefix = Regex("""^(?:[-–•*·]\s*|ebbol\s+|amelybol\s+|of which\s*|bunlardan\s+|bundan\s+|o cumleden\s+|o cumlədən\s+|- )+""")

internal fun fold(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ı', 'i').replace('ə', 'e'), Normalizer.Form.NFD)
    .replace(combiningMarks, "")

/**
 * Lowercase, accents removed, and the OCR confusions that matter for numbers fixed: "O,5 g",
 * "l2 g" and "I2 g" are digits read as letters. They're fixed before folding, which would turn the
 * capital I into an i that no longer looks like a 1.
 */
internal fun normalizeRow(row: String): String = fold(row.replace(letterO, "0").replace(letterL, "1"))
    .replace(spaces, " ")
    .trim()

/** A decimal as labels print it, with a comma or a point. */
internal fun String.toLabelNumber(): Double = replace(',', '.').toDouble()
