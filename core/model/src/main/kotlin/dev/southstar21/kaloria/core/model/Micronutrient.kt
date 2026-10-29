package dev.southstar21.kaloria.core.model

enum class NutrientUnit(val symbol: String) { Gram("g"), Milligram("mg"), Microgram("µg") }

/**
 * Micronutrients tracked beyond the EU mandatory label set. [key] is the stable id used in the
 * database, the PocketBase JSON and the scanner prompt; never rename it.
 */
enum class Micronutrient(val key: String, val label: String, val unit: NutrientUnit) {
    Sodium("sodium", "Sodium", NutrientUnit.Milligram),
    Potassium("potassium", "Potassium", NutrientUnit.Milligram),
    Calcium("calcium", "Calcium", NutrientUnit.Milligram),
    Iron("iron", "Iron", NutrientUnit.Milligram),
    Magnesium("magnesium", "Magnesium", NutrientUnit.Milligram),
    Zinc("zinc", "Zinc", NutrientUnit.Milligram),
    Cholesterol("cholesterol", "Cholesterol", NutrientUnit.Milligram),
    MonounsaturatedFat("monounsaturated_fat", "Monounsaturated fat", NutrientUnit.Gram),
    PolyunsaturatedFat("polyunsaturated_fat", "Polyunsaturated fat", NutrientUnit.Gram),
    TransFat("trans_fat", "Trans fat", NutrientUnit.Gram),
    AddedSugars("added_sugars", "Added sugars", NutrientUnit.Gram),
    VitaminA("vitamin_a", "Vitamin A", NutrientUnit.Microgram),
    VitaminC("vitamin_c", "Vitamin C", NutrientUnit.Milligram),
    VitaminD("vitamin_d", "Vitamin D", NutrientUnit.Microgram),
    VitaminE("vitamin_e", "Vitamin E", NutrientUnit.Milligram),
    VitaminK("vitamin_k", "Vitamin K", NutrientUnit.Microgram),
    VitaminB1("vitamin_b1", "Thiamin (B1)", NutrientUnit.Milligram),
    VitaminB2("vitamin_b2", "Riboflavin (B2)", NutrientUnit.Milligram),
    VitaminB3("vitamin_b3", "Niacin (B3)", NutrientUnit.Milligram),
    VitaminB6("vitamin_b6", "Vitamin B6", NutrientUnit.Milligram),
    VitaminB12("vitamin_b12", "Vitamin B12", NutrientUnit.Microgram),
    Folate("folate", "Folate", NutrientUnit.Microgram),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun fromKey(key: String): Micronutrient? = byKey[key]
    }
}
