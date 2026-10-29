package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** The prompt and the parsing of Gemini Nano's answer, kept free of Android so it's unit-tested. */
object NanoResponseParser {

    val prompt: String = """
        You read nutrition labels. The photo shows a food package's nutrition table; the OCR text
        below was read from the same photo and may contain recognition errors. The label may be in
        Hungarian, English, Turkish or Azerbaijani.

        Return ONLY a JSON object, no prose, with values PER 100 g (or per 100 ml for drinks),
        never per portion. Use null for anything not printed on the label. Do not estimate.
        {
          "per": "100g" or "100ml",
          "energy_kcal": number|null,
          "energy_kj": number|null,
          "fat_g": number|null,
          "saturated_fat_g": number|null,
          "carbohydrates_g": number|null,
          "sugars_g": number|null,
          "fiber_g": number|null,
          "protein_g": number|null,
          "salt_g": number|null,
          "micros": { "<key>": number },
          "name": string|null,
          "brand": string|null
        }
        Micro keys and units: ${Micronutrient.entries.joinToString { "${it.key} (${it.unit.symbol})" }}.
        "<0,5 g" means 0.5. A comma is a decimal separator.

        OCR text:
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parses the model's reply; null if it isn't usable JSON. Checks are added by the caller. */
    fun parse(reply: String, ocrText: String): LabelExtraction? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(reply.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null

        fun num(key: String): Double? = (obj[key] as? JsonPrimitive)?.let {
            it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
        }?.takeIf { it >= 0 }

        val kcal = num("energy_kcal")
        val kj = num("energy_kj")
        val warnings = mutableListOf<FieldWarning>()
        val energy = kcal ?: kj?.let {
            warnings += FieldWarning("energy", "Only kJ was found; kcal was calculated from it.")
            it / Nutrients.KJ_PER_KCAL
        }
        val micros = (obj["micros"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
            val micro = Micronutrient.fromKey(key) ?: return@mapNotNull null
            (value as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0 }?.let { micro to it }
        }.toMap()

        val nutrients = Nutrients(
            energyKcal = energy,
            protein = num("protein_g"),
            carbohydrates = num("carbohydrates_g"),
            sugars = num("sugars_g"),
            fat = num("fat_g"),
            saturatedFat = num("saturated_fat_g"),
            fiber = num("fiber_g"),
            salt = num("salt_g"),
            micros = micros,
        )
        val per = (obj["per"] as? JsonPrimitive)?.contentOrNull?.lowercase().orEmpty()
        return LabelExtraction(
            nutrients = nutrients,
            unit = if ("ml" in per) AmountUnit.Millilitre else AmountUnit.Gram,
            method = ExtractionMethod.GeminiNano,
            warnings = warnings + LabelChecks.check(nutrients) + ocrDisagreements(nutrients, ocrText),
            name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            brand = (obj["brand"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            rawText = ocrText,
        )
    }

    /**
     * A second opinion: where the rule parser read a different number from the OCR text, the
     * model may have hallucinated or misread, so the field is flagged for review.
     */
    private fun ocrDisagreements(model: Nutrients, ocrText: String): List<FieldWarning> {
        val rules = NutritionLabelParser.parse(ocrText.lines()).nutrients
        return listOf(
            Triple("energy", model.energyKcal, rules.energyKcal),
            Triple("protein", model.protein, rules.protein),
            Triple("carbohydrates", model.carbohydrates, rules.carbohydrates),
            Triple("sugars", model.sugars, rules.sugars),
            Triple("fat", model.fat, rules.fat),
            Triple("saturatedFat", model.saturatedFat, rules.saturatedFat),
            Triple("fiber", model.fiber, rules.fiber),
            Triple("salt", model.salt, rules.salt),
        ).mapNotNull { (field, a, b) ->
            if (a != null && b != null && kotlin.math.abs(a - b) > maxOf(0.15, b * 0.03)) {
                FieldWarning(field, "AI read ${a.trimmed()}, the text reads ${b.trimmed()}. Check the photo.")
            } else {
                null
            }
        }
    }

    private fun Double.trimmed() = if (this == Math.floor(this)) toLong().toString() else toString()
    private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> = this ?: emptyMap()
}
