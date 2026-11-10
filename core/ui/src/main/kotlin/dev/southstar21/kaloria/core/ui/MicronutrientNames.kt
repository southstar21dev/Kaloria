package dev.southstar21.kaloria.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.southstar21.kaloria.core.model.Micronutrient

/** The nutrient's name for display, from resources; [Micronutrient.label] stays the English one. */
@get:StringRes
val Micronutrient.nameRes: Int
    get() = when (this) {
        Micronutrient.Sodium -> R.string.micronutrient_sodium
        Micronutrient.Potassium -> R.string.micronutrient_potassium
        Micronutrient.Calcium -> R.string.micronutrient_calcium
        Micronutrient.Iron -> R.string.micronutrient_iron
        Micronutrient.Magnesium -> R.string.micronutrient_magnesium
        Micronutrient.Zinc -> R.string.micronutrient_zinc
        Micronutrient.Cholesterol -> R.string.micronutrient_cholesterol
        Micronutrient.MonounsaturatedFat -> R.string.micronutrient_monounsaturated_fat
        Micronutrient.PolyunsaturatedFat -> R.string.micronutrient_polyunsaturated_fat
        Micronutrient.TransFat -> R.string.micronutrient_trans_fat
        Micronutrient.AddedSugars -> R.string.micronutrient_added_sugars
        Micronutrient.VitaminA -> R.string.micronutrient_vitamin_a
        Micronutrient.VitaminC -> R.string.micronutrient_vitamin_c
        Micronutrient.VitaminD -> R.string.micronutrient_vitamin_d
        Micronutrient.VitaminE -> R.string.micronutrient_vitamin_e
        Micronutrient.VitaminK -> R.string.micronutrient_vitamin_k
        Micronutrient.VitaminB1 -> R.string.micronutrient_vitamin_b1
        Micronutrient.VitaminB2 -> R.string.micronutrient_vitamin_b2
        Micronutrient.VitaminB3 -> R.string.micronutrient_vitamin_b3
        Micronutrient.VitaminB6 -> R.string.micronutrient_vitamin_b6
        Micronutrient.VitaminB12 -> R.string.micronutrient_vitamin_b12
        Micronutrient.Folate -> R.string.micronutrient_folate
    }

@Composable
fun Micronutrient.displayName(): String = stringResource(nameRes)
