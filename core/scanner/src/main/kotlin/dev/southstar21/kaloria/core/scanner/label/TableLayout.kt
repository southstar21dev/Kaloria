package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit

/**
 * Which value column holds the per-100 values, and per what, from the table's column headings.
 * Most labels put per 100 first, but some lead with the portion ("1 adagban (30 g) | 100 g-ban").
 *
 * @property column the per-100 column, 0 for the first.
 * @property perPortion the headings name a portion but no per-100 column, so the values may be per portion.
 * @property headed a per-100 heading was read, so [column] is known rather than assumed to be the first.
 */
internal data class TableLayout(val column: Int, val unit: AmountUnit, val perPortion: Boolean, val headed: Boolean) {
    companion object {
        /** The per-100 column's heading: "100 g", "100g-ban", "100 grammban", "100 ml-ben", "100 q". */
        private val per100 = Regex("""(?<![\d.,])100\s*(ml|g(?:r|ramm?\p{L}*)?|q)(?!\p{L})""")

        /** A portion column's heading: "1 adagban (30 g)", "per serving", "porsiyon". */
        private val portion = Regex("""(?<![\d.,])\d+(?:[.,]\d+)?\s*(?:g|ml|q)(?!\p{L})|$PORTION_WORDS""")
        private val portionWord = Regex(PORTION_WORDS)
        private val letter = Regex("""\p{L}""")

        /** A per-100 heading with no more than this many other letters is a heading, not a sentence. */
        private const val HEADING_LETTERS = 12

        /** The layout of the table in normalised [rows]; headings are looked for above the first nutrient. */
        fun of(rows: List<String>, isNutrientRow: (String) -> Boolean): TableLayout {
            val firstNutrient = rows.indexOfFirst(isNutrientRow).takeIf { it >= 0 } ?: rows.size
            val above = rows.subList(0, firstNutrient)
            val heading = above.lastOrNull { per100.containsMatchIn(it) }
            return heading?.let(::fromHeading) ?: TableLayout(
                column = 0,
                // No headings above the table: a short "100 ml" line elsewhere still says it's a drink,
                // but "prepared with 100 ml milk" in a footnote doesn't.
                unit = if (rows.any { !isNutrientRow(it) && isShortMlHeading(it) }) AmountUnit.Millilitre else AmountUnit.Gram,
                perPortion = above.any { portionWord.containsMatchIn(it) },
                headed = false,
            )
        }

        /** Whether [row] names the per-100 column, which never holds a nutrient's own value. */
        fun isHeading(row: String): Boolean = per100.containsMatchIn(row)

        private fun fromHeading(heading: String): TableLayout {
            val per100s = per100.findAll(heading).toList()
            val portions = portion.findAll(heading).filter { p -> per100s.none { p.range.first in it.range } }
            // Headings in reading order, a heading that names its portion twice ("1 adagban (30 g)") once.
            val kinds = (per100s.map { it.range.first to true } + portions.map { it.range.first to false })
                .sortedBy { it.first }
                .map { it.second }
            val columns = kinds.filterIndexed { i, isPer100 -> i == 0 || isPer100 != kinds[i - 1] }
            return TableLayout(
                column = columns.indexOf(true),
                unit = if (per100s.any { it.groupValues[1] == "ml" }) AmountUnit.Millilitre else AmountUnit.Gram,
                perPortion = false,
                headed = true,
            )
        }

        private fun isShortMlHeading(row: String): Boolean = per100.findAll(row).any { match ->
            match.groupValues[1] == "ml" && letter.findAll(row.removeRange(match.range)).count() <= HEADING_LETTERS
        }
    }
}

private const val PORTION_WORDS = """adag|portion|serving|porsiyon|porsiya|szelet"""
