package dev.southstar21.kaloria.core.network

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.FoodSource
import dev.southstar21.kaloria.core.model.Micronutrient
import dev.southstar21.kaloria.core.model.Nutrients
import dev.southstar21.kaloria.core.model.RemoteFood
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
internal data class UsdaSearchRequest(val query: String, val pageSize: Int, val dataType: List<String>)

@Serializable
internal data class UsdaSearchResponse(val foods: List<UsdaFood> = emptyList())

@Serializable
internal data class UsdaFood(
    val fdcId: Long,
    val description: String,
    val dataType: String? = null,
    val foodNutrients: List<UsdaNutrient> = emptyList(),
)

@Serializable
internal data class UsdaNutrient(val nutrientId: Int, val unitName: String? = null, val value: Double? = null)

/**
 * USDA FoodData Central, for generic foods ("apple, raw") with lab-measured micronutrients.
 * Branded foods are left to Open Food Facts. Values are per 100 g.
 */
class UsdaDataSource @Inject constructor(
    private val client: HttpClient,
    @UsdaApiKey private val apiKey: String,
) {
    suspend fun search(query: String, pageSize: Int = 25): List<RemoteFood> {
        // POST, not GET: USDA's front end intermittently rejects "Survey (FNDDS)" in a query string
        // with a 400 (about 3 in 8 requests when measured), while the JSON body form never failed.
        val response: UsdaSearchResponse = client.post("https://api.nal.usda.gov/fdc/v1/foods/search") {
            parameter("api_key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(UsdaSearchRequest(query, pageSize, listOf("Foundation", "SR Legacy", "Survey (FNDDS)")))
        }.body()
        return response.foods.mapNotNull(::toRemoteFood)
    }

    companion object {
        // FoodData Central nutrient ids.
        private const val ENERGY_KCAL = 1008
        private const val ENERGY_ATWATER_SPECIFIC = 2048
        private const val ENERGY_ATWATER_GENERAL = 2047
        private const val ENERGY_KJ = 1062
        private const val PROTEIN = 1003
        private const val FAT = 1004
        private const val CARBS_BY_DIFFERENCE = 1005
        private const val CARBS_BY_SUMMATION = 1050
        private const val FIBER = 1079
        private const val SUGARS = 2000
        private const val SUGARS_NLEA = 1063
        private const val SATURATED_FAT = 1258
        private const val SODIUM = 1093

        private val micros = mapOf(
            SODIUM to Micronutrient.Sodium,
            1092 to Micronutrient.Potassium,
            1087 to Micronutrient.Calcium,
            1089 to Micronutrient.Iron,
            1090 to Micronutrient.Magnesium,
            1095 to Micronutrient.Zinc,
            1253 to Micronutrient.Cholesterol,
            1292 to Micronutrient.MonounsaturatedFat,
            1293 to Micronutrient.PolyunsaturatedFat,
            1257 to Micronutrient.TransFat,
            1235 to Micronutrient.AddedSugars,
            1106 to Micronutrient.VitaminA,
            1162 to Micronutrient.VitaminC,
            1114 to Micronutrient.VitaminD,
            1109 to Micronutrient.VitaminE,
            1185 to Micronutrient.VitaminK,
            1165 to Micronutrient.VitaminB1,
            1166 to Micronutrient.VitaminB2,
            1167 to Micronutrient.VitaminB3,
            1175 to Micronutrient.VitaminB6,
            1178 to Micronutrient.VitaminB12,
            1190 to Micronutrient.Folate,
        )

        internal fun toRemoteFood(food: UsdaFood): RemoteFood? {
            val byId = food.foodNutrients.filter { it.value != null }.associate { it.nutrientId to it.value!! }
            val kcal = byId[ENERGY_KCAL] ?: byId[ENERGY_ATWATER_SPECIFIC] ?: byId[ENERGY_ATWATER_GENERAL]
                ?: byId[ENERGY_KJ]?.let { it / Nutrients.KJ_PER_KCAL } ?: return null
            return RemoteFood(
                source = FoodSource.Usda,
                sourceRef = food.fdcId.toString(),
                name = food.description,
                brand = null,
                barcode = null,
                unit = AmountUnit.Gram,
                nutrients = Nutrients(
                    energyKcal = kcal,
                    protein = byId[PROTEIN],
                    carbohydrates = byId[CARBS_BY_DIFFERENCE] ?: byId[CARBS_BY_SUMMATION],
                    sugars = byId[SUGARS] ?: byId[SUGARS_NLEA],
                    fat = byId[FAT],
                    saturatedFat = byId[SATURATED_FAT],
                    fiber = byId[FIBER],
                    salt = byId[SODIUM]?.let { it * 2.5 / 1000 },
                    micros = micros.mapNotNull { (id, m) -> byId[id]?.let { m to it } }.toMap(),
                ),
                detail = food.dataType?.let { "USDA $it" },
            )
        }
    }
}
