package dev.southstar21.kaloria.core.scanner.label

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class NanoStatus { Available, Downloadable, Downloading, Unavailable }

/**
 * The label to read: the nutrition table cropped and flattened when it was found ([flattened]),
 * else the photo as taken, and its text as table rows.
 */
class FlatLabel(val image: Bitmap, val rows: List<String>, val flattened: Boolean)

/**
 * Photo of a nutrition table → values to review. On-device only: ML Kit text recognition finds the
 * table, which is cropped and flattened ([flatten]), even round a can, and read again, then Gemini Nano reads the flat
 * table when the phone has it (e.g. Galaxy S26), else [NutritionLabelParser] reads its text.
 */
@Singleton
class LabelScanner @Inject constructor(private val vision: LabelVision) {
    private val model by lazy { Generation.getClient() }

    suspend fun nanoStatus(): NanoStatus = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> NanoStatus.Available
            FeatureStatus.DOWNLOADABLE -> NanoStatus.Downloadable
            FeatureStatus.DOWNLOADING -> NanoStatus.Downloading
            else -> NanoStatus.Unavailable
        }
    }.getOrElse { if (it is CancellationException) throw it else NanoStatus.Unavailable }

    /** Downloads the model if needed; returns once it's ready (or throws). */
    suspend fun downloadNano() {
        if (nanoStatus() == NanoStatus.Downloadable || nanoStatus() == NanoStatus.Downloading) model.download().collect()
    }

    /**
     * Finds the nutrition table in [photo] and turns it into a flat, upright crop of just the
     * table: what both the text recognition and Gemini Nano read best. A photo where no table is
     * found comes back as it is.
     */
    suspend fun flatten(photo: Bitmap): FlatLabel {
        // The table is found on a smaller copy, which is quicker to read and plenty to locate it,
        // then cut from the full photo so its small print keeps every pixel.
        val small = withContext(Dispatchers.Default) { photo.shrunkTo(FIND_LONG_SIDE) }
        val lines = vision.recognize(small)
        debug { "lines: " + lines.joinToString { "[${it.text}|${it.corners.joinToString(" ") { p -> "${p.x.toInt()},${p.y.toInt()}" }}]" } }
        val asTaken = FlatLabel(small, rowsOf(lines), flattened = false)
        val surface = LabelSurface.fit(lines)?.scaled(photo.width.toFloat() / small.width)
            ?: return asTaken.also { debug { "no table found" } }
        debug { "photo ${photo.width}x${photo.height}, table ${surface.width.toInt()}x${surface.height.toInt()}, curved ${surface.isCurved}" }
        var flat = withContext(Dispatchers.Default) { vision.flatten(photo, surface, FLAT_LONG_SIDE, MAX_UPSCALE) }
        var flatLines = vision.recognize(flat)
        val nutrients = { r: List<String> -> r.count(NutritionLabelParser::isNutrientRow) }
        // Round a can the first fit leaves some bend, most at the far rows. Read flat, the text is
        // far easier to fit: fit what's left there, and draw again through both fits at once, so
        // the photo is still sampled only once.
        if (surface.isCurved) {
            LabelSurface.fit(flatLines)?.takeIf { it.isCurved }?.let { rest ->
                val w = flat.width.toFloat()
                val h = flat.height.toFloat()
                val again = withContext(Dispatchers.Default) {
                    vision.flatten(photo, rest.width / w * surface.width, rest.height / h * surface.height, curved = true, FLAT_LONG_SIDE, MAX_UPSCALE) { u, v ->
                        val p = rest.map(u, v)
                        surface.map(p.x / w, p.y / h)
                    }
                }
                val againLines = vision.recognize(again)
                debug { "second fit: ${nutrients(rowsOf(againLines))} nutrient rows, was ${nutrients(rowsOf(flatLines))}" }
                if (nutrients(rowsOf(againLines)) >= nutrients(rowsOf(flatLines))) {
                    flat = again
                    flatLines = againLines
                }
            }
        }
        val rows = rowsOf(flatLines)
        // Keep the flat crop only if it reads at least as many nutrients as the whole photo did.
        return if (nutrients(rows) >= nutrients(asTaken.rows)) FlatLabel(flat, rows, flattened = true) else asTaken
    }

    /** @param forceRules skip Gemini Nano, for comparing against the fallback. */
    suspend fun read(label: FlatLabel, forceRules: Boolean = false): LabelExtraction {
        val text = NutritionLabelParser.fixMisreadGrams(label.rows).joinToString("\n")
        debug { "image ${label.image.width}x${label.image.height}, flattened ${label.flattened}, rows:\n$text" }
        if (!forceRules && nanoStatus() == NanoStatus.Available) {
            val viaNano = runCatching {
                withTimeout(NANO_TIMEOUT_MS) {
                    val reply = model.generateContent(
                        generateContentRequest(ImagePart(label.image), TextPart(NanoResponseParser.prompt + "\n" + text)) {
                            temperature = 0f
                            topK = 1
                        },
                    ).candidates.firstOrNull()?.text.orEmpty()
                    debug { "nano reply:\n$reply" }
                    NanoResponseParser.parse(reply, text)
                }
            }.onFailure {
                debug { "nano failed: $it" }
                if (it is CancellationException && it !is kotlinx.coroutines.TimeoutCancellationException) throw it
            }
                .getOrNull()
            if (viaNano != null && viaNano.isUsable) return viaNano
        }
        return NutritionLabelParser.parse(label.rows)
    }

    private fun Bitmap.shrunkTo(longSide: Int): Bitmap {
        val factor = longSide.toFloat() / maxOf(width, height)
        return if (factor >= 1f) this else scale((width * factor).toInt(), (height * factor).toInt())
    }

    private companion object {
        const val NANO_TIMEOUT_MS = 45_000L

        /** Longest side of the copy the table is looked for in. */
        const val FIND_LONG_SIDE = 2048

        /** The flat table's longer side, px: small print comes out ~20 px tall on a typical label. */
        const val FLAT_LONG_SIDE = 1600f

        /** A table that filled little of the photo is enlarged, but not past where it just blurs. */
        const val MAX_UPSCALE = 2.5f
    }
}
