package dev.southstar21.kaloria.core.scanner.label

import dev.southstar21.kaloria.core.model.AmountUnit
import dev.southstar21.kaloria.core.model.Nutrients

enum class ExtractionMethod { GeminiNano, Rules }

/** A field the reviewer should look at twice, with why. */
data class FieldWarning(val field: String, val message: String)

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
