package dev.southstar21.kaloria.core.scanner.label

/** A nutrient's value as read, in grams, with what the reviewer should know about it. */
internal data class Reading(val grams: Double, val kind: WarningKind? = null, val args: List<String> = emptyList())

/** Reads the values on a normalised table row: the nutrient's own, or a row holding only values. */
internal object CellValues {
    /** A value: "<" when the label says "less than", the number, and its unit if it has one. */
    private val value = Regex("""(?<![\d.,])(<\s*)?(\d+(?:[.,]\d+)?)\s*(mg|mcg|[µμu]g|g|q|ml|%|kj|kcal)?(?![\p{L}\d])""")

    /** Numbers that are never a nutrient's value: a share of the daily intake ("17 %"), a volume, energy. */
    private val notValues = setOf("%", "ml", "kj", "kcal")

    /** "Nátrium (mg) 120": the unit given once, beside the name. */
    private val unitByName = Regex("""\(\s*(mg|mcg|[µμu]g|g)\s*\)""")

    /** Asides such as "(N x 6,25)" or "(37%)", whose numbers aren't the value. */
    private val parenthesised = Regex("""\([^)]*\)""")
    private val leadingWords = Regex("""^[\p{L} /,.%:*-]+""")
    private val leadingPunctuation = Regex("""^[\s:*.,-]+""")
    private val trace = Regex("""^(?:traces?|nyomokban|nyomnyi|eser miktarda)(?!\p{L})""")
    private val traceAnywhere = Regex("""(?<!\p{L})(?:traces?|nyomokban|nyomnyi|eser miktarda)(?!\p{L})""")
    private val startsWithValue = Regex("""^<?\s*\d""")

    /** "1,200 mg": milligrams run into the thousands, where a three-place decimal makes no sense. */
    private val thousands = Regex("""[1-9]\d{0,2}[.,]\d{3}""")

    private val micrograms = setOf("mcg", "µg", "μg", "ug")

    /**
     * Sodium per 100 g is at most a few grams, so a bare number above this is milligrams with the
     * unit lost or never printed.
     */
    private const val SODIUM_MAX_GRAMS = 5.0

    /**
     * The value in [column] on [row] (0 is the first value column), in grams. A share of the daily
     * intake ("17 %") is not a column. A row saying "trace" reads 0.
     */
    fun read(row: String, column: Int, sodium: Boolean): Reading? {
        val stripped = row.replace(subRowPrefix, "")
        val namedUnit = unitByName.find(stripped)?.groupValues?.get(1).orEmpty()
        val withoutAsides = stripped.replace(parenthesised, " ")
        val cell = withoutAsides.replaceFirst(leadingWords, "")
        val match = value.findAll(cell).filter { it.groupValues[3] !in notValues }.elementAtOrNull(column)
        return match?.let { reading(it, namedUnit, sodium) } ?: Reading(0.0, WarningKind.Trace).takeIf { traceAnywhere.containsMatchIn(withoutAsides) }
    }

    /** A row that holds only values, such as the value column read apart from its names. */
    fun isValueRow(row: String): Boolean = startsWithValue.containsMatchIn(row.replace(subRowPrefix, ""))

    /**
     * Whether what follows a short nutrient name is its value, or nothing (the value is on another
     * row): "Só 1,8 g" is salt, "So 100% natural" isn't.
     */
    fun followsName(rest: String): Boolean {
        val cell = rest.replace(parenthesised, " ").replaceFirst(leadingPunctuation, "")
        val first = value.find(cell)?.takeIf { it.range.first == 0 }
        return cell.isEmpty() || trace.containsMatchIn(cell) || (first != null && first.groupValues[3] !in notValues)
    }

    private fun reading(match: MatchResult, namedUnit: String, sodium: Boolean): Reading {
        val unit = match.groupValues[3].ifEmpty { namedUnit }
        val digits = match.groupValues[2]
        val small = unit == "mg" || unit in micrograms
        val number = (if (small && thousands.matches(digits)) digits.filter(Char::isDigit) else digits).toLabelNumber()
        val guessedMg = unit.isEmpty() && sodium && number > SODIUM_MAX_GRAMS
        val grams = when {
            unit == "mg" || guessedMg -> number / 1_000
            unit in micrograms -> number / 1_000_000
            else -> number
        }
        return when {
            match.groupValues[1].isNotEmpty() -> Reading(grams, WarningKind.LessThan, listOf(if (unit.isEmpty()) digits else "$digits $unit"))
            guessedMg -> Reading(grams, WarningKind.SodiumUnitGuessed)
            else -> Reading(grams)
        }
    }
}
