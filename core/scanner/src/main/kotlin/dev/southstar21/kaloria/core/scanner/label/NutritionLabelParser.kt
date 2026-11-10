package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients

/**
 * Reads an EU-style nutrition table from OCR text without any model: keyword dictionaries for
 * Hungarian, English, Turkish and Azerbaijani, and each nutrient's value in the per-100 column
 * (the first, unless the column headings put the portion first).
 */
object NutritionLabelParser {

    /** A nutrient row, and the key the food editor knows its field by. */
    private enum class Field(val key: String) {
        Energy("energy"),
        Fat("fat"),
        Saturated("saturatedFat"),
        Carbs("carbohydrates"),
        Sugars("sugars"),
        Fiber("fiber"),
        Protein("protein"),
        Salt("salt"),

        // Sodium is reviewed as the salt it's turned into.
        Sodium("salt"),
    }

    /**
     * Row prefixes, matched against diacritic-free lowercase text so OCR that drops accents
     * ("feherje", "szenhidrat", "seker") still matches. More specific fields come first:
     * "telített zsírsavak" must not be read as fat, "cukrok" not as carbs.
     */
    private val keywords: List<Pair<Field, List<String>>> = listOf(
        Field.Saturated to listOf("telitett", "saturate", "saturated", "doymus", "of which saturates"),
        Field.Sugars to listOf("cukr", "cukor", "sugar", "seker", "şəkər").map(::fold),
        Field.Fiber to listOf("rost", "elelmi rost", "fibre", "fiber", "lif", "diyet lif", "posa"),
        Field.Energy to listOf("energia", "energy", "enerji", "energetikai", "kaloria", "enerji degeri", "enerji dəyəri").map(::fold),
        Field.Fat to listOf("zsir", "fat", "yag", "yağ", "yaglar").map(::fold),
        Field.Carbs to listOf("szenhidrat", "carbohydrate", "karbonhidrat", "karbohidrat", "carbs").map(::fold),
        Field.Protein to listOf("feherje", "protein", "zulal", "zülal").map(::fold),
        Field.Sodium to listOf("natrium", "sodium", "sodyum"),
        Field.Salt to listOf("so", "salt", "tuz", "duz"),
    )

    /** Rows that start like a nutrient but aren't one: sugar alcohols are not sugars. */
    private val notNutrients = listOf("cukoralkohol", "sugar alcohol", "polyol", "poliol", "seker alkol")

    /** "Total fat", "Toplam yağ", "Összes cukor": a qualifier before the name. */
    private val qualifier = Regex("""^(?:total|toplam|osszes)\s+""")

    /** Names this short only count with a value after them; see [CellValues.followsName]. */
    private const val SHORT_NAME = 3

    /** No nutrient has more grams per 100 g than this. */
    private const val MAX_GRAMS = 100.0

    /**
     * Undoes OCR reading "g" as "9" ("17 g" as "179"); see [MisreadGrams]. Only on a table whose
     * values are in grams, and never on the energy row.
     */
    fun fixMisreadGrams(rows: List<String>): List<String> = MisreadGrams.fix(rows, ::isEnergyRow).rows

    fun parse(rows: List<String>): LabelExtraction = read(rows).let { it.copy(warnings = it.warnings + LabelChecks.check(it.nutrients)) }

    /** What [parse] reads, with only the warnings about how each value was read, not the checks. */
    internal fun read(rows: List<String>): LabelExtraction = Reader(rows).extraction()

    /** Whether [row] starts with a nutrient name, e.g. to find where the table is in a photo. */
    fun isNutrientRow(row: String): Boolean = fieldOf(normalizeRow(row)) != null

    private fun isEnergyRow(row: String): Boolean = normalizeRow(row).let { fieldOf(it) == Field.Energy || EnergyText.hasUnit(it) }

    private fun fieldOf(row: String): Field? {
        val text = row.replace(subRowPrefix, "").replace(qualifier, "")
        if (notNutrients.any { text.startsWith(it) }) return null
        return keywords.firstOrNull { (_, words) -> words.any { word -> text.isNamed(word) } }?.first ?: nearMiss(text)
    }

    private fun String.isNamed(word: String): Boolean = startsWith(word) && when {
        word.length > SHORT_NAME || length == word.length -> true
        this[word.length].isLetter() -> false
        else -> CellValues.followsName(substring(word.length))
    }

    /**
     * Small print, curved or blurred, comes out a letter off ("energja", "feheie"): a first word of
     * five letters or more, about as long as a keyword and within one edit of it (two for seven
     * letters or more), still counts.
     */
    private fun nearMiss(text: String): Field? {
        val first = text.takeWhile { it.isLetter() }
        return if (first.length < 5) {
            null
        } else {
            keywords.firstOrNull { (_, words) ->
                words.any { word ->
                    val head = word.substringBefore(' ')
                    head.length >= 5 && kotlin.math.abs(head.length - first.length) <= 1 &&
                        editDistance(first, head) <= (if (head.length >= 7) 2 else 1)
                }
            }?.first
        }
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length]
    }

    /** One parse of one table. */
    private class Reader(private val raw: List<String>) {
        private val fixed = MisreadGrams.fix(raw, ::isEnergyRow)
        private val rows = fixed.rows.map(::normalizeRow)
        private val unfixed = raw.map(::normalizeRow)
        private val layout = TableLayout.of(rows) { fieldOf(it) != null }
        private val values = mutableMapOf<Field, Double>()
        private val warnings = mutableListOf<FieldWarning>()
        private var kj: Double? = null

        /** Rows whose numbers a nutrient on another row has taken. */
        private val borrowed = mutableSetOf<Int>()

        fun extraction(): LabelExtraction {
            rows.forEachIndexed { index, row ->
                val field = fieldOf(row)
                if (field == Field.Energy && field !in values) {
                    readEnergy(index)
                } else if (field != null && field !in values) {
                    readValue(field, index)
                }
            }
            if (layout.perPortion) warnings += FieldWarning(Field.Energy.key, WarningKind.PerPortion)
            if (!layout.headed && !layout.perPortion && hasSecondColumn()) warnings += FieldWarning(Field.Energy.key, WarningKind.ColumnGuessed)
            val salt = values[Field.Salt]
            val sodium = values[Field.Sodium]
            val nutrients = Nutrients(
                energyKcal = energyKcal(),
                protein = values[Field.Protein],
                carbohydrates = values[Field.Carbs],
                sugars = values[Field.Sugars],
                fat = values[Field.Fat],
                saturatedFat = values[Field.Saturated],
                fiber = values[Field.Fiber],
                salt = salt ?: sodium?.let { it * 2.5 },
                micros = sodium?.let { mapOf(Micronutrient.Sodium to it * 1000) }.orEmpty(),
            )
            return LabelExtraction(
                nutrients = nutrients,
                unit = layout.unit,
                method = ExtractionMethod.Rules,
                warnings = warnings,
                rawText = raw.joinToString("\n"),
            )
        }

        /**
         * Nutrient rows carry a second value, as in a per-100 | per-portion table. With no heading
         * read, nothing says which column is per 100; most labels put it first, which is what's read.
         * A "trace" row reads as a value in any column, so it doesn't count.
         */
        private fun hasSecondColumn(): Boolean = rows.count { row ->
            val field = fieldOf(row)
            field != null && field != Field.Energy && CellValues.read(row, 1, sodium = false)?.takeIf { it.kind != WarningKind.Trace } != null
        } >= 2

        private fun readEnergy(index: Int) {
            var text = rows[index]
            var energy = EnergyText.read(text, layout.column)
            // Energy may wrap onto the rows below: "Energia" / "1338 kJ / 320 kcal", or
            // "Energia 1338 kJ" / "320 kcal". Those rows are its own, never another nutrient's.
            var next = index + 1
            while (!energy.isComplete && next <= index + 2 && continuesEnergy(next, energy.isEmpty)) {
                text += " " + rows[next]
                borrowed += next++
                energy = EnergyText.read(text, layout.column)
            }
            energy.kcal?.let { values[Field.Energy] = it }
            kj = energy.kj
        }

        private fun continuesEnergy(index: Int, nothingYet: Boolean): Boolean = rows.getOrNull(index)?.let { row ->
            fieldOf(row) == null && !TableLayout.isHeading(row) && (EnergyText.hasUnit(row) || (nothingYet && CellValues.isValueRow(row)))
        } == true

        private fun readValue(field: Field, index: Int) {
            val sodium = field == Field.Sodium
            val (source, reading) = CellValues.read(rows[index], layout.column, sodium)?.let { index to it }
                ?: neighbourOf(index)?.let { i -> CellValues.read(rows[i], layout.column, sodium)?.let { i to it } }
                ?: return
            borrowed += source
            values[field] = reading.grams
            reading.kind?.let { warnings += FieldWarning(field.key, it, reading.args) }
            if (misread(source, sodium, reading.grams)) warnings += FieldWarning(field.key, WarningKind.GramReadAsNine)
        }

        /**
         * The row holding a nutrient's value when its own row has none: the row above when the name
         * wrapped ("ebből 17 g 5,1 g" / "telített zsírsavak"), else the row below (a value set under
         * its name). Only a row of values, not a heading or the energy row's continuation.
         */
        private fun neighbourOf(index: Int): Int? = listOf(index - 1, index + 1).firstOrNull { i ->
            i !in borrowed && rows.getOrNull(i)?.let { row ->
                fieldOf(row) == null && !TableLayout.isHeading(row) && !EnergyText.hasUnit(row) && CellValues.isValueRow(row)
            } == true
        }

        /**
         * Whether the value came from a 9 taken for a misread "g" that nothing confirms: on a
         * single-column table "Salt 1,29" may really be 1.29. Read with the 9, "179" would be more
         * than 100 g per 100 g, so that fix is certain.
         */
        private fun misread(source: Int, sodium: Boolean, grams: Double): Boolean {
            val asPrinted = if (unfixed[source] == rows[source]) null else CellValues.read(unfixed[source], layout.column, sodium)?.grams
            return asPrinted != null && asPrinted != grams && asPrinted <= MAX_GRAMS && !MisreadGrams.confirms(rows[source], fixed.ratio)
        }

        private fun energyKcal(): Double? {
            val kcal = values[Field.Energy]
            val kj = kj
            if (kcal != null && kj != null && EnergyText.disagree(kcal, kj)) {
                warnings += FieldWarning(Field.Energy.key, WarningKind.EnergyUnitsDisagree)
            }
            if (kcal == null && kj != null) warnings += FieldWarning(Field.Energy.key, WarningKind.KcalFromKj)
            return kcal ?: kj?.let { it / Nutrients.KJ_PER_KCAL }
        }
    }
}
