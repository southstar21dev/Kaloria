package dev.southstar21.kaloria.core.scanner.label

import kotlin.math.abs
import kotlin.math.ln

/**
 * Undoes OCR's commonest misreading on these tables, "g" read as "9": "17 g" comes out "179",
 * "0,4 g" as "0,4 9". Only on a table whose values are in grams, and never on the energy row.
 *
 * A real trailing 9 looks the same once a row has lost its unit, so on a table with a portion
 * column the two readings are weighed against the ratio between the columns, which every row
 * shares: "Carbs 59 17,7 g" keeps its 59 when the other rows run about 3.3 to 1.
 */
internal object MisreadGrams {
    /** A gram value: a number and "g" ("24 g", "13g"); there are several on any table in grams. */
    private val gramValue = Regex("""\d\s*g\b""")

    /** "179": "17 g" with its "g" read as a 9, glued on. A unit or word after it means it's real. */
    private val nineGlued = Regex("""(?<![\d.,])(\d+(?:[.,]\d+)?)9(?=\s*$|\s+[^\p{L}%µ])""")

    /** "0,4 9": the same, with the space kept. */
    private val nineApart = Regex("""(?<![\d.,])(\d+(?:[.,]\d+)?)\s9(?=\s*$|\s+[^\p{L}%µ])""")

    /** A row of two gram values after a name: "Zsír 44 g 13 g". */
    private val gramPair = Regex("""^\D*?(\d+(?:[.,]\d+)?)\s*g\s+(\d+(?:[.,]\d+)?)\s*g\s*$""")
    private val number = Regex("""(?<![\d.,])\d+(?:[.,]\d+)?""")

    /** How far a row's ratio may stray from the table's and still match it: values are rounded. */
    private const val RATIO_TOLERANCE = 0.1

    /** The rows fixed, and the per-100 to per-portion ratio when the table has a portion column. */
    class Fixed(val rows: List<String>, val ratio: Double?)

    fun fix(rows: List<String>, isEnergyRow: (String) -> Boolean): Fixed {
        if (rows.count { gramValue.containsMatchIn(it) } < 3) return Fixed(rows, null)
        val ratio = columnRatio(rows)
        return Fixed(rows.map { row -> if (isEnergyRow(row)) row else fixRow(row, ratio) }, ratio)
    }

    /** Whether [row] (normalised) keeps the table's column [ratio], which a misread value wouldn't. */
    fun confirms(row: String, ratio: Double?): Boolean = ratio != null && ratioOf(row)?.let { fits(it, ratio) } == true

    private fun fixRow(row: String, ratio: Double?): String {
        val fixed = row.replace(nineApart, "$1 g").replace(nineGlued, "$1 g")
        val keep = ratio != null && fixed != row && confirms(normalizeRow(row), ratio) && !confirms(normalizeRow(fixed), ratio)
        return if (keep) row else fixed
    }

    /** The median ratio of the rows printed cleanly in both columns; null for a single column. */
    private fun columnRatio(rows: List<String>): Double? {
        val ratios = rows.mapNotNull { row ->
            gramPair.find(normalizeRow(row))?.let { m ->
                val a = m.groupValues[1].toLabelNumber()
                val b = m.groupValues[2].toLabelNumber()
                (a / b).takeIf { a > 0 && b > 0 }
            }
        }.sorted()
        return ratios.takeIf { it.size >= 2 }?.let { it[it.size / 2] }
    }

    private fun ratioOf(row: String): Double? = number.findAll(row).map { it.value.toLabelNumber() }.toList()
        .takeIf { it.size == 2 && it[1] > 0 }
        ?.let { (a, b) -> a / b }

    private fun fits(ratio: Double, expected: Double) = ratio > 0 && abs(ln(ratio / expected)) < RATIO_TOLERANCE
}
