package dev.southstar21.kaloria.core.network

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.RemoteFood
import dev.southstar21.kaloria.core.model.Serving
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPathPart
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

private const val FIELDS =
    "code,product_name,product_name_en,product_name_hu,brands,quantity,product_quantity_unit," +
        "serving_quantity,serving_size,nutriments"

class OpenFoodFactsDataSource @Inject constructor(private val client: HttpClient) {

    /**
     * The product with [barcode], or null if Open Food Facts doesn't know it or has no energy value.
     * A dropped connection, a timeout or a busy server is retried three times before it counts as a failure.
     */
    suspend fun product(barcode: String): RemoteFood? {
        val response = productResponse(barcode) ?: return null
        return response.takeIf { it["status"]?.jsonPrimitive?.intOrNull == 1 }?.get("product")?.jsonObject?.let(::parseProduct)
    }

    /** The lookup's answer; null when Open Food Facts doesn't know the product. */
    private suspend fun productResponse(barcode: String): JsonObject? = try {
        retryingTransient {
            client.get("https://world.openfoodfacts.org/api/v2/product/${barcode.encodeURLPathPart()}") {
                parameter("fields", FIELDS)
            }.body<JsonObject>()
        }
    } catch (e: ClientRequestException) {
        // An unknown product is a 404 (with a `status: 0` body), not an outage.
        if (e.response.status == HttpStatusCode.NotFound) null else throw e
    }

    /** Products matching [query]; transient failures are retried like [product]'s. */
    suspend fun search(query: String, pageSize: Int = 20): List<RemoteFood> {
        val response: JsonObject = retryingTransient {
            client.get("https://search.openfoodfacts.org/search") {
                parameter("q", query)
                parameter("page_size", pageSize)
                parameter("langs", "en,hu")
                parameter("fields", FIELDS)
            }.body()
        }
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

/** Retries [block] after a short pause when it fails for a reason that's likely gone a moment later. */
internal suspend fun <T> retryingTransient(delaysMillis: List<Long> = listOf(400, 1_000, 2_000), block: suspend () -> T): T {
    for (wait in delaysMillis) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException || !e.isTransient()) throw e
            delay(wait)
        }
    }
    return block()
}

/**
 * Network hiccups and an overloaded server. A 404 or bad request is not worth retrying, and neither is
 * a 429: retrying within seconds only extends the throttling, and search sends a new request with
 * the next keystroke anyway.
 */
private fun Exception.isTransient(): Boolean = when (this) {
    is HttpRequestTimeoutException, is IOException -> true
    is ResponseException -> response.status.value >= 500
    else -> false
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

/** OFF sometimes sends numbers as strings ("1.8"). */
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let { p ->
    p.doubleOrNull ?: p.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
}

private fun JsonElement?.orEmpty(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
