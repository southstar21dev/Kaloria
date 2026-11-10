package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Nutrients

enum class ExtractionMethod { GeminiNano, Rules }

/**
 * Why a field needs a second look. Screens map each kind to their own string resource, filling in
 * [FieldWarning.args]; [english] is the text until they do.
 */
enum class WarningKind(val english: String) {
    /** args: the bound as printed, e.g. "0,5 g". */
    LessThan("Label says “less than %s”; the upper bound was used."),
    Trace("Label says “trace”; 0 was used."),
    KcalFromKj("Only kJ was readable; kcal was calculated from it."),
    EnergyUnitsDisagree("kJ and kcal on the label don't agree; check the energy value."),
    SodiumUnitGuessed("Sodium had no unit; it was read as milligrams."),
    PerPortion("The table seems to give values per portion, not per 100; check every value."),
    ColumnGuessed("No column heading was readable, so the first column was taken as per 100; check every value."),
    GramReadAsNine("The last 9 may be a misread “g”; check this value."),

    /** args: the kcal the macros imply. */
    EnergyVsMacros("Energy doesn't match the macros (they imply %s kcal)."),
    EnergyOver920("More than 920 kcal per 100 is impossible."),
    SugarsOverCarbs("Sugars are higher than carbohydrates."),
    SaturatedOverFat("Saturated fat is higher than fat."),
    Over100Grams("More than 100 g per 100 is impossible."),
    NotRead("Not read from the label."),
    ModelMissed("The AI missed this; it was read from the label's text."),
    ModelZero("The AI read 0 here, and the label's text couldn't confirm it."),

    /** args: the model's value, then the text's. */
    ModelDisagrees("AI read %s, the text reads %s. Check the photo."),
}

/**
 * A field the reviewer should look at twice, with why. [field] is the food editor's key for it:
 * energy, protein, carbohydrates, sugars, fat, saturatedFat, fiber or salt.
 */
data class FieldWarning(val field: String, val kind: WarningKind, val args: List<String> = emptyList()) {
    val message: String get() = kind.english.format(*args.toTypedArray())
}

/** What was read off a nutrition label. Always reviewed by the user before it's saved. */
data class LabelExtraction(
    val nutrients: Nutrients,
    val unit: AmountUnit,
    val method: ExtractionMethod,
    val warnings: List<FieldWarning> = emptyList(),
    val name: String? = null,
    val brand: String? = null,
    val rawText: String = "",
) {
    /** Enough was read to be worth reviewing rather than starting from a blank form. */
    val isUsable: Boolean get() = nutrients.energyKcal != null || listOfNotNull(nutrients.protein, nutrients.carbohydrates, nutrients.fat).size >= 2
}
