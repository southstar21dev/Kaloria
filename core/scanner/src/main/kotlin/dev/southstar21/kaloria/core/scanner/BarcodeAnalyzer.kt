package dev.southstar21.kaloria.core.scanner

import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Where a read barcode sits in what the preview shows: its edges as fractions of the visible
 * area's width and height (the frame's crop rect, which a CameraX viewport makes match the screen).
 */
data class BarcodeSighting(
    val value: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Reports product barcodes (EAN-13/8, UPC-A/E) seen in camera frames, and on every analysed frame
 * where one is ([onSighting], null when there's none), so the UI can frame it.
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
    private val onSighting: (BarcodeSighting?) -> Unit = {},
) : ImageAnalysis.Analyzer {
    /**
     * Applies ML Kit's zoom suggestion and returns whether it did. Many phone cameras (Galaxy main
     * cameras especially) can't focus close up, so a barcode held near is blurry; zooming in on a
     * small, distant one lets people hold the phone back, where it's sharp.
     */
    @Volatile var onZoom: ((Float) -> Boolean)? = null

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .setZoomSuggestionOptions(
                ZoomSuggestionOptions.Builder { ratio -> onZoom?.invoke(ratio) ?: false }
                    .setMaxSupportedZoomRatio(MAX_ZOOM)
                    .build(),
            )
            .build(),
    )

    private val confirm = ReadConfirmer()

    @ExperimentalGetImage
    override fun analyze(proxy: ImageProxy) {
        val image = proxy.image ?: return proxy.close()
        val rotation = proxy.imageInfo.rotationDegrees
        // ML Kit reports boxes in the upright image; turn the visible crop the same way up to match.
        val visible = upright(proxy.cropRect, rotation, proxy.width, proxy.height)
        // A frame that can't be handed to ML Kit is closed here, or CameraX stops sending frames.
        val task = runCatching { scanner.process(InputImage.fromMediaImage(image, rotation)) }.getOrElse {
            proxy.close()
            return
        }
        task
            .addOnSuccessListener { codes ->
                // ML Kit reads the whole sensor frame, not just the part on screen, so a code beside
                // the view (or a second one on the pack) could win. Only codes centred on screen
                // count, and of those the one nearest the middle, where people hold what they mean.
                val inView = codes.filter { it.rawValue != null && it.boundingBox?.let { b -> visible.contains(b.centerX(), b.centerY()) } == true }
                val read = inView
                    .minByOrNull { code ->
                        val b = code.boundingBox!!
                        val dx = (b.exactCenterX() - visible.exactCenterX()) / visible.width()
                        val dy = (b.exactCenterY() - visible.exactCenterY()) / visible.height()
                        dx * dx + dy * dy
                    }
                    // A code already handled is neither framed nor read, as if it weren't there.
                    ?.takeUnless { confirm.isIgnored(it.rawValue) }
                onSighting(
                    read?.let { code ->
                        val box = code.boundingBox!!
                        BarcodeSighting(
                            code.rawValue!!,
                            (box.left - visible.left) / visible.width().toFloat(),
                            (box.top - visible.top) / visible.height().toFloat(),
                            (box.right - visible.left) / visible.width().toFloat(),
                            (box.bottom - visible.top) / visible.height().toFloat(),
                        )
                    },
                )
                val value = read?.rawValue
                val short = read?.format == Barcode.FORMAT_EAN_8 || read?.format == Barcode.FORMAT_UPC_E
                // Every analysed frame counts, a code read or not: frames without an ignored code are how it's known to be gone.
                val confirmed = confirm.read(value, short, nowNanos = System.nanoTime(), inView = inView.mapNotNull { it.rawValue }.toSet())
                if (confirmed && value != null) onBarcode(value)
            }
            .addOnCompleteListener { proxy.close() }
    }

    /**
     * Ignores [code] until it has been out of view for a moment: after its result is dismissed
     * the product is usually still in front of the camera. Call on the main thread, where ML Kit
     * delivers its reads.
     */
    fun ignoreUntilGone(code: String) = confirm.ignoreUntilGone(code)

    fun close() = scanner.close()

    private fun upright(rect: Rect, rotation: Int, width: Int, height: Int): Rect = uprightEdges(rect.left, rect.top, rect.right, rect.bottom, rotation, width, height).let { Rect(it[0], it[1], it[2], it[3]) }

    private companion object {
        const val MAX_ZOOM = 3f
    }
}

/**
 * Whether a read can be trusted: the same value read in several frames close together. A single
 * frame can misread a digit that still passes the check digit (two frames of the same blur can
 * agree on it too), and a partly seen EAN-13 can come out as a valid EAN-8 or UPC-E, so short codes
 * need more agreement. Frames where nothing was read in between (motion blur) don't reset the
 * count; a different value does. Costs ~70 ms for a normal code, ~130 ms for a short one.
 *
 * A code already handled can be ignored until it has been missing from [goneNanos] of analysed
 * frames, so rescanning with the product still in view doesn't bring its result straight back.
 */
internal class ReadConfirmer(
    private val needed: Int = 3,
    private val neededShort: Int = 5,
    private val windowNanos: Long = 1_000_000_000L,
    private val goneNanos: Long = 1_000_000_000L,
) {
    private var last: String? = null
    private var count = 0
    private var at = 0L
    private var ignored: String? = null

    /** When the ignored code was first missing from a frame; null while it's still seen. */
    private var goneSince: Long? = null

    fun ignoreUntilGone(value: String) {
        ignored = value
        goneSince = null
        last = null
        count = 0
    }

    fun isIgnored(value: String?): Boolean = value != null && value == ignored

    /**
     * Whether [value], this frame's read (null when it read nothing), is now confirmed. [inView] is
     * every code the frame showed, to tell when an ignored one has gone.
     */
    fun read(value: String?, short: Boolean, nowNanos: Long, inView: Set<String> = setOfNotNull(value)): Boolean {
        forgetIfGone(inView, nowNanos)
        if (value == null || value == ignored) return false
        if (value != last || nowNanos - at > windowNanos) {
            last = value
            count = 0
        }
        at = nowNanos
        count++
        return count >= if (short) neededShort else needed
    }

    private fun forgetIfGone(inView: Set<String>, nowNanos: Long) {
        val code = ignored ?: return
        if (code in inView) {
            goneSince = null
        } else {
            val since = goneSince ?: nowNanos.also { goneSince = it }
            if (nowNanos - since >= goneNanos) {
                ignored = null
                goneSince = null
            }
        }
    }
}

/**
 * The edges (left, top, right, bottom) of a rect in a [width] x [height] image buffer, as they lie
 * once the buffer is turned [rotation] degrees clockwise to stand upright.
 */
internal fun uprightEdges(left: Int, top: Int, right: Int, bottom: Int, rotation: Int, width: Int, height: Int): IntArray = when (rotation) {
    90 -> intArrayOf(height - bottom, left, height - top, right)
    180 -> intArrayOf(width - right, height - bottom, width - left, height - top)
    270 -> intArrayOf(top, width - right, bottom, width - left)
    else -> intArrayOf(left, top, right, bottom)
}
