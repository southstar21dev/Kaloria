package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** The prompt and the parsing of Gemini Nano's answer, kept free of Android so it's unit-tested. */
object NanoResponseParser {

    val prompt: String = """
        You read nutrition labels. The image is a food package's nutrition table, cropped and
        straightened. The OCR text below was read from the same image, one table row per line, and
        may contain recognition errors; trust the image where they differ. The label may be in
        Hungarian, English, Turkish or Azerbaijani.

        Tables often have two value columns: per 100 g (or 100 ml) and per portion (e.g. "30 g",
        "1 adag"). The column headings say which is which; usually per 100 is the first. Take every
        value from the per-100 column only, never from the portion column.

        Return ONLY a JSON object, no prose. Use null for anything not printed on the label. Do not
        estimate.
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

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Parses the model's reply; null if it isn't usable JSON. Checks are added by the caller. */
    fun parse(reply: String, ocrText: String): LabelExtraction? {
        // An object, or now and then a list of them (the model answering per column): the first.
        val start = reply.indexOfFirst { it == '{' || it == '[' }
        val end = reply.indexOfLast { it == '}' || it == ']' }
        if (start < 0 || end <= start) return null
        val element = runCatching { json.parseToJsonElement(reply.substring(start, end + 1)) }.getOrNull() ?: return null
        val obj = (element as? JsonObject) ?: ((element as? JsonArray)?.firstOrNull() as? JsonObject) ?: return null

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

        // What the model left out but the text plainly has, the text rules fill in, flagged.
        val rules = NutritionLabelParser.parse(ocrText.lines()).nutrients
        fun orRules(field: String, model: Double?, rule: Double?): Double? = model ?: rule?.also {
            warnings += FieldWarning(field, "The AI missed this; it was read from the label's text.")
        }
        val nutrients = Nutrients(
            energyKcal = orRules("energy", energy, rules.energyKcal),
            protein = orRules("protein", num("protein_g"), rules.protein),
            carbohydrates = orRules("carbohydrates", num("carbohydrates_g"), rules.carbohydrates),
            sugars = orRules("sugars", num("sugars_g"), rules.sugars),
            fat = orRules("fat", num("fat_g"), rules.fat),
            saturatedFat = orRules("saturatedFat", num("saturated_fat_g"), rules.saturatedFat),
            fiber = orRules("fiber", num("fiber_g"), rules.fiber),
            salt = orRules("salt", num("salt_g"), rules.salt),
            micros = micros,
        )
        val per = (obj["per"] as? JsonPrimitive)?.contentOrNull?.lowercase().orEmpty()
        return LabelExtraction(
            nutrients = nutrients,
            unit = if ("ml" in per) AmountUnit.Millilitre else AmountUnit.Gram,
            method = ExtractionMethod.GeminiNano,
            warnings = warnings + LabelChecks.check(nutrients) + ocrDisagreements(nutrients, rules),
            name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            brand = (obj["brand"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            rawText = ocrText,
        )
    }

    /**
     * A second opinion: where the rule parser read a different number from the OCR text, the
     * model may have hallucinated or misread, so the field is flagged for review.
     */
    private fun ocrDisagreements(model: Nutrients, rules: Nutrients): List<FieldWarning> = listOf(
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

    private fun Double.trimmed() = if (this == Math.floor(this)) toLong().toString() else toString()
    private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> = this ?: emptyMap()
}
