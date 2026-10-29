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

    fun parse(rows: List<String>): LabelExtraction {
        val normalized = rows.map(::normalize)
        val values = mutableMapOf<Field, Double>()
        val warnings = mutableListOf<FieldWarning>()
        var energyKj: Double? = null

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
            val rest = row.removeKeyword()
            val match = number.find(rest) ?: number.find(normalized.getOrElse(index + 1) { "" }.takeIf { fieldOf(it) == null } ?: "")
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

    private fun fieldOf(row: String): Field? {
        val text = row.replace(subRowPrefix, "")
        return keywords.firstOrNull { (_, words) ->
            words.any { word -> text.startsWith(word) && (word.length > 3 || text.length == word.length || !text[word.length].isLetter()) }
        }?.first
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

internal fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT).replace('ı', 'i').replace('ə', 'e'), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
