package dev.southstar21.kaloria.core.network

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.model.Serving
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val FIELDS =
    "code,product_name,product_name_en,product_name_hu,brands,quantity,product_quantity_unit," +
        "serving_quantity,serving_size,nutriments"

class OpenFoodFactsDataSource @Inject constructor(private val client: HttpClient) {

    /** The product with [barcode], or null if Open Food Facts doesn't know it or has no energy value. */
    suspend fun product(barcode: String): RemoteFood? {
        val response: JsonObject = client.get("https://world.openfoodfacts.org/api/v2/product/$barcode") {
            parameter("fields", FIELDS)
        }.body()
        if (response["status"]?.jsonPrimitive?.intOrNull != 1) return null
        return response["product"]?.jsonObject?.let(::parseProduct)
    }

    suspend fun search(query: String, pageSize: Int = 20): List<RemoteFood> {
        val response: JsonObject = client.get("https://search.openfoodfacts.org/search") {
            parameter("q", query)
            parameter("page_size", pageSize)
            parameter("langs", "en,hu")
            parameter("fields", FIELDS)
        }.body()
        return response["hits"].orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseProduct) }
    }

    companion object {
        /** OFF's per-100 nutrient keys, all of which it stores in grams. */
        private val microKeys = mapOf(
            "sodium" to Micronutrient.Sodium,
            "potassium" to Micronutrient.Potassium,
            "calcium" to Micronutrient.Calcium,
            "iron" to Micronutrient.Iron,
            "magnesium" to Micronutrient.Magnesium,
            "zinc" to Micronutrient.Zinc,
            "cholesterol" to Micronutrient.Cholesterol,
            "monounsaturated-fat" to Micronutrient.MonounsaturatedFat,
            "polyunsaturated-fat" to Micronutrient.PolyunsaturatedFat,
            "trans-fat" to Micronutrient.TransFat,
            "added-sugars" to Micronutrient.AddedSugars,
            "vitamin-a" to Micronutrient.VitaminA,
            "vitamin-c" to Micronutrient.VitaminC,
            "vitamin-d" to Micronutrient.VitaminD,
            "vitamin-e" to Micronutrient.VitaminE,
            "vitamin-k" to Micronutrient.VitaminK,
            "vitamin-b1" to Micronutrient.VitaminB1,
            "vitamin-b2" to Micronutrient.VitaminB2,
            "vitamin-pp" to Micronutrient.VitaminB3,
            "vitamin-b6" to Micronutrient.VitaminB6,
            "vitamin-b12" to Micronutrient.VitaminB12,
            "vitamin-b9" to Micronutrient.Folate,
        )

        internal fun parseProduct(product: JsonObject): RemoteFood? {
            val code = product.string("code") ?: return null
            val n = product["nutriments"] as? JsonObject ?: return null
            val kcal = n.double("energy-kcal_100g") ?: n.double("energy_100g")?.let { it / Nutrients.KJ_PER_KCAL }
                ?: return null
            val name = listOf("product_name", "product_name_en", "product_name_hu")
                .firstNotNullOfOrNull { product.string(it)?.takeIf(String::isNotBlank) } ?: return null
            val unit = if (isLiquid(product)) AmountUnit.Millilitre else AmountUnit.Gram

            val micros = microKeys.mapNotNull { (key, micro) ->
                n.double("${key}_100g")?.let { grams -> micro to grams.toUnit(micro) }
            }.toMap()

            val servingQuantity = product.double("serving_quantity")
            val servings = if (servingQuantity != null && servingQuantity > 0) {
                listOf(Serving(product.string("serving_size")?.let { "1 serving ($it)" } ?: "1 serving", servingQuantity))
            } else {
                emptyList()
            }

            return RemoteFood(
                source = FoodSource.OpenFoodFacts,
                sourceRef = code,
                name = name.trim(),
                brand = product.brands(),
                barcode = code,
                unit = unit,
                nutrients = Nutrients(
                    energyKcal = kcal,
                    protein = n.double("proteins_100g"),
                    carbohydrates = n.double("carbohydrates_100g"),
                    sugars = n.double("sugars_100g"),
                    fat = n.double("fat_100g"),
                    saturatedFat = n.double("saturated-fat_100g"),
                    fiber = n.double("fiber_100g"),
                    salt = n.double("salt_100g"),
                    micros = micros,
                ),
                servings = servings,
            )
        }

        /** OFF labels drinks' values `_100g` too; the pack size tells us they're per 100 ml. */
        private fun isLiquid(product: JsonObject): Boolean {
            val unit = product.string("product_quantity_unit")?.lowercase()
            if (unit != null) return unit == "ml" || unit == "l" || unit == "cl"
            val quantity = product.string("quantity")?.lowercase() ?: return false
            return Regex("""\d\s*(ml|cl|l)\b""").containsMatchIn(quantity)
        }

        /** v2 returns brands as "A, B"; the search API as ["A", "B"]. */
        private fun JsonObject.brands(): String? = when (val b = this["brands"]) {
            is JsonArray -> b.mapNotNull { it.jsonPrimitive.contentOrNull }.firstOrNull()
            is JsonPrimitive -> b.contentOrNull?.split(',')?.firstOrNull()
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }

        private fun Double.toUnit(micro: Micronutrient) = when (micro.unit) {
            dev.southstar21.kaloria.core.model.NutrientUnit.Gram -> this
            dev.southstar21.kaloria.core.model.NutrientUnit.Milligram -> this * 1_000
            dev.southstar21.kaloria.core.model.NutrientUnit.Microgram -> this * 1_000_000
        }
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** OFF sometimes sends numbers as strings ("1.8"). */
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let { p ->
    p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
}

private fun JsonElement?.orEmpty(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
