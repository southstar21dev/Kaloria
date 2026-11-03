package dev.southstar21.kaloria.core.scanner.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Text recognition and flattening, shared by the live camera preview and the photo that's read:
 * reads a picture's lines and words, and draws the table flat given its [LabelSurface].
 */
@Singleton
class LabelVision @Inject constructor() {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): List<OcrLine> = withContext(Dispatchers.Default) {
        linesOf(recognizer.process(InputImage.fromBitmap(bitmap, 0)).await())
    }

    /** [recognize], blocking: for the camera's analysis thread. */
    fun recognizeNow(bitmap: Bitmap): List<OcrLine> = linesOf(Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))))

    private fun linesOf(text: Text): List<OcrLine> = text.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        val words = line.elements.mapNotNull { word ->
            word.cornerPoints?.takeIf { it.size == 4 }?.let { c -> OcrWord(word.text, c.map { Pt(it.x.toFloat(), it.y.toFloat()) }) }
        }
        val corners = line.cornerPoints?.takeIf { it.size == 4 }?.map { Pt(it.x.toFloat(), it.y.toFloat()) }
        if (corners != null) {
            OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.angle, corners, words)
        } else {
            OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.angle, words = words)
        }
    }

    /**
     * Draws the table flat from [photo], its longer side [longSide] px (enlarged at most
     * [maxUpscale] times). A flat table is one perspective transform; a curved one is drawn
     * through a fine grid, each cell its own small perspective transform, so rows bend straight
     * and squeezed parts widen. Anything outside the photo comes out white.
     */
    fun flatten(photo: Bitmap, surface: LabelSurface, longSide: Float, maxUpscale: Float): Bitmap = flatten(photo, surface.width, surface.height, surface.isCurved, longSide, maxUpscale, surface::map)

    /**
     * [flatten] through any map from the flat table (u, v in 0..1) to [photo]'s pixels, for a table
     * of natural size [naturalWidth] x [naturalHeight].
     */
    fun flatten(
        photo: Bitmap,
        naturalWidth: Float,
        naturalHeight: Float,
        curved: Boolean,
        longSide: Float,
        maxUpscale: Float,
        map: (Float, Float) -> Pt,
    ): Bitmap {
        val scale = (longSide / maxOf(naturalWidth, naturalHeight)).coerceAtMost(maxUpscale)
        val width = (naturalWidth * scale).toInt().coerceAtLeast(1)
        val height = (naturalHeight * scale).toInt().coerceAtLeast(1)
        val cols = if (curved) CURVED_COLS else 1
        val rows = if (curved) CURVED_ROWS else 1
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { flat ->
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            for (j in 0 until rows) {
                for (i in 0 until cols) {
                    val u0 = i / cols.toFloat()
                    val u1 = (i + 1) / cols.toFloat()
                    val v0 = j / rows.toFloat()
                    val v1 = (j + 1) / rows.toFloat()
                    val a = map(u0, v0)
                    val b = map(u1, v0)
                    val c = map(u1, v1)
                    val d = map(u0, v1)
                    val l = u0 * width
                    val r = u1 * width
                    val t = v0 * height
                    val btm = v1 * height
                    if (!matrix.setPolyToPoly(floatArrayOf(a.x, a.y, b.x, b.y, c.x, c.y, d.x, d.y), 0, floatArrayOf(l, t, r, t, r, btm, l, btm), 0, 4)) continue
                    canvas.save()
                    // A hair past the cell, so neighbouring cells meet without a seam.
                    canvas.clipRect(l - 0.5f, t - 0.5f, r + 0.5f, btm + 0.5f)
                    canvas.drawBitmap(photo, matrix, paint)
                    canvas.restore()
                }
            }
        }
    }

    private companion object {
        const val CURVED_COLS = 24
        const val CURVED_ROWS = 12
    }
}

/** Off unless turned on: adb shell setprop log.tag.LabelScan DEBUG */
private const val TAG = "LabelScan"

/** A diagnostic line for the label scanner, when its log is turned on. */
internal inline fun debug(message: () -> String) {
    if (Log.isLoggable(TAG, Log.DEBUG)) message().chunked(3000).forEach { Log.d(TAG, it) }
}
