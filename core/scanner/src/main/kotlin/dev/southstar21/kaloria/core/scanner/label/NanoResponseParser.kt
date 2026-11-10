package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
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

    /** A value the model wrote as a string: "2,0", "<0,5", "12 g", "1 338". */
    private val leadingNumber = Regex("""^\s*(<)?\s*(\d+(?:[.,]\d+)?)""")
    private val digitGroupSpace = Regex("""(?<=\d)[ \u00A0](?=\d{3}(?!\d))""")

    /** The fields besides energy: the editor's key, the reply's key, and the value in [Nutrients]. */
    private class Slot(val field: String, val key: String, val of: (Nutrients) -> Double?)

    private val slots = listOf(
        Slot("protein", "protein_g", Nutrients::protein),
        Slot("carbohydrates", "carbohydrates_g", Nutrients::carbohydrates),
        Slot("sugars", "sugars_g", Nutrients::sugars),
        Slot("fat", "fat_g", Nutrients::fat),
        Slot("saturatedFat", "saturated_fat_g", Nutrients::saturatedFat),
        Slot("fiber", "fiber_g", Nutrients::fiber),
        Slot("salt", "salt_g", Nutrients::salt),
    )

    /** Parses the model's reply; null if it isn't usable JSON or read nothing. Checks are added here too. */
    fun parse(reply: String, ocrText: String): LabelExtraction? = objectIn(reply)?.let { extraction(it, ocrText) }

    /** An object, or now and then a list of them (the model answering per column): the first. */
    private fun objectIn(reply: String): JsonObject? {
        val start = reply.indexOfFirst { it == '{' || it == '[' }
        val end = reply.indexOfLast { it == '}' || it == ']' }
        val element = if (start < 0 || end <= start) null else runCatching { json.parseToJsonElement(reply.substring(start, end + 1)) }.getOrNull()
        return (element as? JsonObject) ?: ((element as? JsonArray)?.firstOrNull() as? JsonObject)
    }

    private fun extraction(obj: JsonObject, ocrText: String): LabelExtraction? {
        val warnings = mutableListOf<FieldWarning>()
        fun num(key: String, field: String): Double? = numberOf(obj[key])?.let { (value, lessThan) ->
            if (lessThan) warnings += FieldWarning(field, WarningKind.LessThan, listOf(value.trimmed()))
            value
        }
        val model = mapOf("energy" to energy(num("energy_kcal", "energy"), num("energy_kj", "energy"), warnings)) +
            slots.associate { it.field to num(it.key, it.field) }
        // Nothing but nulls and zeros: the model read nothing, and the text rules do better alone.
        if (model.values.none { it != null && it > 0 }) return null

        // What the model left out but the text plainly has, the text rules fill in, flagged, with
        // what the rules noted about reading it.
        val rules = NutritionLabelParser.read(ocrText.lines())
        val ruled = rules.nutrients.byField()
        val values = model.mapValues { (field, value) -> settle(field, value, ruled[field], rules, warnings) }
        val nutrients = Nutrients(
            energyKcal = values["energy"],
            protein = values["protein"],
            carbohydrates = values["carbohydrates"],
            sugars = values["sugars"],
            fat = values["fat"],
            saturatedFat = values["saturatedFat"],
            fiber = values["fiber"],
            salt = values["salt"],
            // Sodium the text rules read reaches the micros too, unless the model gave its own.
            micros = rules.nutrients.micros + microsIn(obj),
        )
        val per = (obj["per"] as? JsonPrimitive)?.contentOrNull?.lowercase().orEmpty()
        return LabelExtraction(
            nutrients = nutrients,
            unit = if ("ml" in per) AmountUnit.Millilitre else AmountUnit.Gram,
            method = ExtractionMethod.GeminiNano,
            warnings = warnings + LabelChecks.check(nutrients) + ocrDisagreements(nutrients, rules.nutrients),
            name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            brand = (obj["brand"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" },
            rawText = ocrText,
        )
    }

    /** kcal, or kcal worked out from kJ, with kJ checked against kcal when the model gave both. */
    private fun energy(kcal: Double?, kj: Double?, warnings: MutableList<FieldWarning>): Double? {
        if (kcal != null && kj != null && EnergyText.disagree(kcal, kj)) warnings += FieldWarning("energy", WarningKind.EnergyUnitsDisagree)
        if (kcal == null && kj != null) warnings += FieldWarning("energy", WarningKind.KcalFromKj)
        return kcal ?: kj?.let { it / Nutrients.KJ_PER_KCAL }
    }

    /**
     * The model's value, or the rules' when the model has none. A 0 from the model where the text
     * reads more is a miss too (models write 0 for "not found"), and one the text can't confirm is
     * kept but flagged.
     */
    private fun settle(field: String, model: Double?, rule: Double?, rules: LabelExtraction, warnings: MutableList<FieldWarning>): Double? = when {
        model != null && (model > 0 || rule == null || rule == 0.0) -> model.also {
            if (model == 0.0 && rule == null) warnings += FieldWarning(field, WarningKind.ModelZero)
        }

        rule != null -> rule.also {
            warnings += FieldWarning(field, WarningKind.ModelMissed)
            warnings += rules.warnings.filter { it.field == field }
        }

        else -> null
    }

    /** A number, or a number written as a string; never negative, infinite or NaN. With whether it said "less than". */
    private fun numberOf(element: JsonElement?): Pair<Double, Boolean>? {
        val primitive = element as? JsonPrimitive
        val parsed = when {
            primitive == null || primitive is JsonNull -> null

            primitive.isString -> leadingNumber.find(primitive.content.replace(digitGroupSpace, ""))
                ?.let { it.groupValues[2].toLabelNumber() to it.groupValues[1].isNotEmpty() }

            else -> primitive.doubleOrNull?.let { it to false }
        }
        return parsed?.takeIf { it.first.isFinite() && it.first >= 0 }
    }

    private fun microsIn(obj: JsonObject): Map<Micronutrient, Double> = (obj["micros"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
        Micronutrient.fromKey(key)?.let { micro -> numberOf(value)?.let { micro to it.first } }
    }.toMap()

    private fun Nutrients.byField(): Map<String, Double?> = mapOf("energy" to energyKcal) + slots.associate { it.field to it.of(this) }

    /**
     * A second opinion: where the rule parser read a different number from the OCR text, the
     * model may have hallucinated or misread, so the field is flagged for review.
     */
    private fun ocrDisagreements(model: Nutrients, rules: Nutrients): List<FieldWarning> {
        val ruled = rules.byField()
        return model.byField().mapNotNull { (field, a) ->
            val b = ruled[field]
            if (a != null && b != null && kotlin.math.abs(a - b) > maxOf(0.15, b * 0.03)) {
                FieldWarning(field, WarningKind.ModelDisagrees, listOf(a.trimmed(), b.trimmed()))
            } else {
                null
            }
        }
    }

    private fun Double.trimmed() = if (this == Math.floor(this)) toLong().toString() else toString()
    private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
}
