package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import java.text.Normalizer
import java.util.Locale

/**
 * Reads an EU-style nutrition table from OCR text without any model: keyword dictionaries for
 * Hungarian, English, Turkish and Azerbaijani, and the first value after each nutrient (labels
 * put the per-100 column before per-portion).
 */
object NutritionLabelParser {

    private enum class Field { Energy, Fat, Saturated, Carbs, Sugars, Fiber, Protein, Salt, Sodium }

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
        Field.Protein to listOf("feherje", "protein", "zulal", "zülal", "белок").map(::fold),
        Field.Sodium to listOf("natrium", "sodium", "sodyum"),
        Field.Salt to listOf("so", "salt", "tuz", "duz"),
    )

    /** Words that may precede a sub-row: "ebből", "of which", "bunlardan", "o cümlədən", "-". */
    private val subRowPrefix = Regex("""^(?:[-–•*·]\s*|ebbol\s+|amelybol\s+|of which\s*|bunlardan\s+|bundan\s+|o cumleden\s+|o cumlədən\s+|- )+""")

    private val number = Regex("""(<\s*)?(\d+(?:[.,]\d+)?)""")
    private val kcal = Regex("""(\d+(?:[.,]\d+)?)\s*k\s*c\s*a\s*l""")
    private val kj = Regex("""(\d+(?:[.,]\d+)?)\s*k\s*j""")
    private val per100ml = Regex("""100\s*ml""")

    /** A gram value: a number and "g" ("24 g", "13g"); there are several on any table in grams. */
    private val gramValue = Regex("""\d\s*g\b""")

    /** "179": "17 g" with its "g" read as a 9, glued on. A unit or word after it means it's real. */
    private val nineGlued = Regex("""(?<![\d.,])(\d+(?:[.,]\d+)?)9(?=\s*$|\s+[^\p{L}%µ])""")

    /** "0,4 9": the same, with the space kept. */
    private val nineApart = Regex("""(?<![\d.,])(\d+(?:[.,]\d+)?)\s9(?=\s*$|\s+[^\p{L}%µ])""")

    /**
     * Undoes OCR's commonest misreading on these tables, "g" read as "9": "17 g" comes out "179",
     * "0,4 g" as "0,4 9". Only on a table whose values are in grams, and never on the energy row.
     */
    fun fixMisreadGrams(rows: List<String>): List<String> {
        if (rows.count { gramValue.containsMatchIn(it) } < 3) return rows
        return rows.map { row ->
            if (fieldOf(normalize(row)) == Field.Energy || kcal.containsMatchIn(normalize(row))) {
                row
            } else {
                row.replace(nineApart, "$1 g").replace(nineGlued, "$1 g")
            }
        }
    }

    fun parse(rows: List<String>): LabelExtraction {
        val normalized = fixMisreadGrams(rows).map(::normalize)
        val values = mutableMapOf<Field, Double>()
        val warnings = mutableListOf<FieldWarning>()
        var energyKj: Double? = null
        // Rows whose numbers a nutrient on another row has taken.
        val borrowed = mutableSetOf<Int>()

        normalized.forEachIndexed { index, row ->
            val field = fieldOf(row) ?: return@forEachIndexed
            if (field in values) return@forEachIndexed
            if (field == Field.Energy) {
                // Energy may wrap onto the next row: "Energia" / "1338 kJ / 320 kcal".
                val text = row + " " + normalized.getOrElse(index + 1) { "" }
                kcal.find(text)?.let { values[Field.Energy] = it.groupValues[1].toNumber() }
                kj.find(text)?.let { energyKj = it.groupValues[1].toNumber() }
                return@forEachIndexed
            }
            // The value is on the row, or, when the name wrapped ("ebből" / "cukrok" with the value
            // beside the first line), on the row above, or (a value set below its name) the row below.
            val rest = row.removeKeyword()
            val neighbour = listOf(index - 1, index + 1).firstOrNull { i ->
                i !in borrowed && normalized.getOrNull(i)?.let { fieldOf(it) == null && number.containsMatchIn(it.removeKeyword()) } == true
            }
            val match = number.find(rest)
                ?: neighbour?.let { i -> number.find(normalized[i].removeKeyword())?.also { borrowed += i } }
                ?: return@forEachIndexed
            values[field] = match.groupValues[2].toNumber()
            if (match.groupValues[1].isNotEmpty()) {
                warnings += FieldWarning(field.name.lowercase(), "Label says “less than ${match.groupValues[2]}”; the upper bound was used.")
            }
        }

        var kcalValue = values[Field.Energy]
        val kjValue = energyKj
        if (kcalValue == null && kjValue != null) {
            kcalValue = kjValue / Nutrients.KJ_PER_KCAL
            warnings += FieldWarning("energy", "Only kJ was readable; kcal was calculated from it.")
        } else if (kcalValue != null && kjValue != null && kotlin.math.abs(kjValue / Nutrients.KJ_PER_KCAL - kcalValue) > maxOf(5.0, kcalValue * 0.05)) {
            warnings += FieldWarning("energy", "kJ and kcal on the label don't agree; check the energy value.")
        }

        val salt = values[Field.Salt]
        val sodiumG = values[Field.Sodium]
        val nutrients = Nutrients(
            energyKcal = kcalValue,
            protein = values[Field.Protein],
            carbohydrates = values[Field.Carbs],
            sugars = values[Field.Sugars],
            fat = values[Field.Fat],
            saturatedFat = values[Field.Saturated],
            fiber = values[Field.Fiber],
            salt = salt ?: sodiumG?.let { it * 2.5 },
            micros = sodiumG?.let { mapOf(Micronutrient.Sodium to it * 1000) }.orEmpty(),
        )
        return LabelExtraction(
            nutrients = nutrients,
            unit = if (normalized.any { per100ml.containsMatchIn(it) }) AmountUnit.Millilitre else AmountUnit.Gram,
            method = ExtractionMethod.Rules,
            warnings = warnings + LabelChecks.check(nutrients),
            rawText = rows.joinToString("\n"),
        )
    }

    /** Whether [row] starts with a nutrient name, e.g. to find where the table is in a photo. */
    fun isNutrientRow(row: String): Boolean = fieldOf(normalize(row)) != null

    private fun fieldOf(row: String): Field? {
        val text = row.replace(subRowPrefix, "")
        keywords.firstOrNull { (_, words) ->
            words.any { word -> text.startsWith(word) && (word.length > 3 || text.length == word.length || !text[word.length].isLetter()) }
        }?.let { return it.first }
        // Small print, curved or blurred, comes out a letter off ("energja", "feheie"): a first word
        // of five letters or more, about as long as a keyword and within one edit of it (two for seven
        // letters or more), still counts.
        val first = text.takeWhile { it.isLetter() }
        if (first.length < 5) return null
        return keywords.firstOrNull { (_, words) ->
            words.any { word ->
                val head = word.substringBefore(' ')
                head.length >= 5 && kotlin.math.abs(head.length - first.length) <= 1 &&
                    editDistance(first, head) <= (if (head.length >= 7) 2 else 1)
            }
        }?.first
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

    /** Drops the leading label words so the first number found is the value, not e.g. "B12". */
    private fun String.removeKeyword(): String = replace(subRowPrefix, "").replaceFirst(Regex("""^[\p{L} ()/,.%-]+"""), "")

    /** Lowercase, accents removed, and the OCR confusions that matter for numbers fixed. */
    private fun normalize(row: String): String {
        val folded = fold(row)
        // "O,5 g" / "l2 g": letters that are really digits when they sit next to digits or a comma.
        return folded
            .replace(Regex("""(?<=[\d,.])[oO]|[oO](?=[\d,.]\d)"""), "0")
            .replace(Regex("""(?<=\d)[lI]|[lI](?=\d)"""), "1")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun String.toNumber() = replace(',', '.').toDouble()
}

internal fun fold(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ı', 'i').replace('ə', 'e'), Normalizer.Form.NFD)
    .replace(Regex("""\p{Mn}+"""), "")
