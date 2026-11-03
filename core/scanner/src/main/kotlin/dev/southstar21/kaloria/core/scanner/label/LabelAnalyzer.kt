package dev.southstar21.kaloria.core.scanner.label

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * The nutrition table as the camera sees it right now. Points are fractions of the visible frame
 * (what the preview shows), so the UI can draw them over it at any size.
 *
 * @property outline the table's edge, clockwise from the top-left.
 * @property grid lines across and down the table's surface, bending with it.
 * @property flat the table drawn flat, small: what will be read.
 */
class LiveLabel(val outline: List<Pt>, val grid: List<List<Pt>>, val flat: Bitmap)

/**
 * Finds and flattens the nutrition table in camera frames as they come, a few times a second, so
 * people see it found, outlined and flattened before they take the photo. Reports null when a
 * frame has no table. Frames that arrive while one is being worked on are dropped (CameraX's
 * keep-only-latest), so it never falls behind.
 */
class LabelAnalyzer(
    private val vision: LabelVision,
    private val onLabel: (LiveLabel?) -> Unit,
) : ImageAnalysis.Analyzer {
    /** While true, frames are dropped unread: nothing to show while a photo is being read. */
    @Volatile var paused = false

    private var lastStarted = 0L

    override fun analyze(proxy: ImageProxy) {
        if (paused) return proxy.close()
        // A few reads a second is as fast as the outline can usefully move; skipping frames before
        // they're converted spares two full-size bitmaps and an OCR pass each.
        val now = System.nanoTime()
        if (now - lastStarted < MIN_INTERVAL_NS) return proxy.close()
        lastStarted = now
        val frame = try {
            proxy.toUprightVisible()
        } catch (e: RuntimeException) {
            null
        } finally {
            proxy.close()
        }
        frame ?: return
        val started = System.nanoTime()
        val live = runCatching {
            val lines = vision.recognizeNow(frame)
            val read = System.nanoTime()
            val surface = LabelSurface.fit(lines)
            debug { "live ${frame.width}x${frame.height}: ${lines.size} lines in ${(read - started) / 1_000_000} ms, table ${surface != null}, curved ${surface?.isCurved}" }
            surface ?: return@runCatching null
            val w = frame.width.toFloat()
            val h = frame.height.toFloat()
            fun Pt.relative() = Pt(x / w, y / h)
            val grid = GRID_DOWN.map { u -> (0..GRID_STEPS).map { k -> surface.map(u, k / GRID_STEPS.toFloat()).relative() } } +
                GRID_ACROSS.map { v -> (0..GRID_STEPS).map { k -> surface.map(k / GRID_STEPS.toFloat(), v).relative() } }
            LiveLabel(
                outline = surface.outline(GRID_STEPS).map { it.relative() },
                grid = grid,
                flat = vision.flatten(frame, surface, PREVIEW_LONG_SIDE, maxUpscale = 1.5f),
            )
        }.getOrNull()
        frame.recycle() // flatten() drew a copy; the full frame is a few MB we don't keep
        debug { "live frame done in ${(System.nanoTime() - started) / 1_000_000} ms" }
        if (!paused) onLabel(live)
    }

    /** The frame turned upright and cropped to what the preview shows (the viewport's crop). */
    private fun ImageProxy.toUprightVisible(): Bitmap {
        val full = toBitmap()
        val crop = cropRect.takeIf { full.width == width && full.height == height && !it.isEmpty }
        val rotate = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
        val upright = if (crop != null) {
            Bitmap.createBitmap(full, crop.left, crop.top, crop.width(), crop.height(), rotate, true)
        } else {
            Bitmap.createBitmap(full, 0, 0, full.width, full.height, rotate, true)
        }
        // createBitmap returns its source when there's nothing to do; otherwise free the source now.
        if (upright !== full) full.recycle()
        return upright
    }

    private companion object {
        /** The live flat preview's longer side, px: enough to see, quick to draw. */
        const val PREVIEW_LONG_SIDE = 640f
        const val MIN_INTERVAL_NS = 300_000_000L
        const val GRID_STEPS = 16
        val GRID_DOWN = listOf(0.25f, 0.5f, 0.75f)
        val GRID_ACROSS = listOf(0.2f, 0.4f, 0.6f, 0.8f)
    }
}
