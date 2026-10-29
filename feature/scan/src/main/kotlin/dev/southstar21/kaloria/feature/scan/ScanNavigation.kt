package dev.southstar21.kaloria.feature.scan

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Scan a barcode and log the product onto [epochDay]. */
@Serializable
data class BarcodeScanKey(val epochDay: Long) : NavKey

/** Photograph a nutrition label to create a food; [barcode] is attached when known. */
@Serializable
data class LabelScanKey(val barcode: String? = null) : NavKey
