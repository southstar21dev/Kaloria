package dev.southstar21.kaloria.feature.foods.editor

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.southstar21.kaloria.core.scanner.label.FieldWarning
import dev.southstar21.kaloria.core.scanner.label.WarningKind
import dev.southstar21.kaloria.feature.foods.R

/** Each label-scan warning's text, filled in with the warning's args in order. */
private val warningTexts = mapOf(
    WarningKind.LessThan to R.string.food_editor_warning_less_than,
    WarningKind.Trace to R.string.food_editor_warning_trace,
    WarningKind.KcalFromKj to R.string.food_editor_warning_kcal_from_kj,
    WarningKind.EnergyUnitsDisagree to R.string.food_editor_warning_energy_units_disagree,
    WarningKind.SodiumUnitGuessed to R.string.food_editor_warning_sodium_unit_guessed,
    WarningKind.PerPortion to R.string.food_editor_warning_per_portion,
    WarningKind.ColumnGuessed to R.string.food_editor_warning_column_guessed,
    WarningKind.GramReadAsNine to R.string.food_editor_warning_gram_read_as_nine,
    WarningKind.EnergyVsMacros to R.string.food_editor_warning_energy_vs_macros,
    WarningKind.EnergyOver920 to R.string.food_editor_warning_energy_over_920,
    WarningKind.SugarsOverCarbs to R.string.food_editor_warning_sugars_over_carbs,
    WarningKind.SaturatedOverFat to R.string.food_editor_warning_saturated_over_fat,
    WarningKind.Over100Grams to R.string.food_editor_warning_over_100_grams,
    WarningKind.NotRead to R.string.food_editor_warning_not_read,
    WarningKind.ModelMissed to R.string.food_editor_warning_model_missed,
    WarningKind.ModelZero to R.string.food_editor_warning_model_zero,
    WarningKind.ModelDisagrees to R.string.food_editor_warning_model_disagrees,
)

/** The string for [kind], or null for a kind added to the scanner without one here yet. */
@StringRes
internal fun warningTextRes(kind: WarningKind): Int? = warningTexts[kind]

/** The warning in the app's language; the scanner's English text for a kind without a string. */
@Composable
internal fun FieldWarning.text(): String = warningTextRes(kind)?.let { stringResource(it, *args.toTypedArray()) } ?: message
