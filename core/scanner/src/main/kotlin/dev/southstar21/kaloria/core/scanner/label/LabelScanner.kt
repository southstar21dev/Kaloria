package dev.southstar21.kaloria.core.scanner.label

import android.graphics.Bitmap
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

enum class NanoStatus { Available, Downloadable, Downloading, Unavailable }

/**
 * Photo of a nutrition table → values to review. On-device only: ML Kit text recognition, then
 * Gemini Nano when the phone has it (e.g. Galaxy S26), else [NutritionLabelParser] on the text.
 */
@Singleton
class LabelScanner @Inject constructor() {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
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

    /** @param forceRules skip Gemini Nano, for comparing against the fallback. */
    suspend fun scan(bitmap: Bitmap, forceRules: Boolean = false): LabelExtraction {
        val rows = recognize(bitmap)
        val text = rows.joinToString("\n")
        if (!forceRules && nanoStatus() == NanoStatus.Available) {
            val viaNano = runCatching {
                withTimeout(NANO_TIMEOUT_MS) {
                    val reply = model.generateContent(
                        generateContentRequest(ImagePart(bitmap), TextPart(NanoResponseParser.prompt + "\n" + text)) {
                            temperature = 0f
                            topK = 1
                        },
                    ).candidates.firstOrNull()?.text.orEmpty()
                    NanoResponseParser.parse(reply, text)
                }
            }.onFailure { if (it is CancellationException && it !is kotlinx.coroutines.TimeoutCancellationException) throw it }
                .getOrNull()
            if (viaNano != null && viaNano.isUsable) return viaNano
        }
        return NutritionLabelParser.parse(rows)
    }

    private suspend fun recognize(bitmap: Bitmap): List<String> = withContext(Dispatchers.Default) {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = result.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom)
        }
        rowsOf(lines)
    }

    private companion object {
        const val NANO_TIMEOUT_MS = 45_000L
    }
}
