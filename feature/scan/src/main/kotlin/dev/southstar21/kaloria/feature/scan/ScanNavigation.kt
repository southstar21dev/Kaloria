package dev.southstar21.kaloria.feature.scan

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Scan a barcode and log the product onto the day [epochDay], or with null onto today as it is when
 * the food is logged, so a scanner left open past midnight logs onto the new day.
 */
@Serializable
data class BarcodeScanKey(val epochDay: Long? = null) : NavKey

/** Photograph a nutrition label to create a food; [barcode] is attached when known. */
@Serializable
data class LabelScanKey(val barcode: String? = null) : NavKey
