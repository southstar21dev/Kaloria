package dev.southstar21.kaloria.feature.scan.label

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.southstar21.kaloria.core.scanner.label.LabelAnalyzer
import dev.southstar21.kaloria.core.scanner.label.LabelDraft
import dev.southstar21.kaloria.core.scanner.label.LabelScanner
import dev.southstar21.kaloria.core.scanner.label.LabelVision
import dev.southstar21.kaloria.core.scanner.label.LiveLabel
import dev.southstar21.kaloria.core.scanner.label.NanoStatus
import dev.southstar21.kaloria.core.scanner.label.ScanDraftStore
import dev.southstar21.kaloria.feature.scan.LabelScanKey
import dev.southstar21.kaloria.feature.scan.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LabelScanState {
    data object Ready : LabelScanState
    data object Reading : LabelScanState
    data class Failed(val message: String) : LabelScanState
}

@HiltViewModel(assistedFactory = LabelScanViewModel.Factory::class)
class LabelScanViewModel @AssistedInject constructor(
    @Assisted private val key: LabelScanKey,
    @ApplicationContext private val context: Context,
    private val scanner: LabelScanner,
    private val drafts: ScanDraftStore,
    vision: LabelVision,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: LabelScanKey): LabelScanViewModel
    }

    var state by mutableStateOf<LabelScanState>(LabelScanState.Ready)
        private set

    var nano by mutableStateOf<NanoStatus?>(null)
        private set

    /** The table as the camera sees it now, outlined and flattened; null while none is in view. */
    var live by mutableStateOf<LiveLabel?>(null)
        private set

    /** Finds the table in the camera's frames as they come, for [live]. */
    val analyzer = LabelAnalyzer(vision) { found -> viewModelScope.launch { if (state != LabelScanState.Reading) live = found } }

    /** The flattened table being read, shown while it is; null otherwise. */
    var flattened by mutableStateOf<Bitmap?>(null)
        private set

    /** Read with the text rules alone, skipping Gemini Nano (the AI chip switches it). */
    var rulesOnly by mutableStateOf(false)

    private val draftChannel = Channel<String>(Channel.CONFLATED)

    /** Id of the draft to review, once the label has been read. */
    val draftReady = draftChannel.receiveAsFlow()

    init {
        viewModelScope.launch { nano = scanner.nanoStatus() }
    }

    fun downloadModel() = viewModelScope.launch {
        nano = NanoStatus.Downloading
        runCatching { scanner.downloadNano() }
        nano = scanner.nanoStatus()
    }

    /**
     * Reads the label in the image [load] produces. Decoding a photo takes long enough to drop
     * frames, so [load] runs off the main thread. Returns false (without calling [load]) while a
     * read is already in progress.
     */
    fun read(load: () -> Bitmap): Boolean {
        if (state == LabelScanState.Reading) return false
        state = LabelScanState.Reading
        analyzer.paused = true
        live = null
        viewModelScope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.Default) { load() }
                val label = scanner.flatten(bitmap)
                if (label.flattened) flattened = label.image
                val extraction = scanner.read(label, forceRules = rulesOnly)
                if (!extraction.isUsable) error(context.getString(R.string.label_scan_no_table))
                // The review shows what was read: the flat table when there is one.
                val photo = withContext(Dispatchers.IO) { savePhoto(label.image) }
                drafts.put(LabelDraft(extraction, photo, key.barcode))
            }.onSuccess { id ->
                state = LabelScanState.Ready
                flattened = null
                analyzer.paused = false
                draftChannel.send(id)
            }.onFailure {
                state = LabelScanState.Failed(it.message ?: context.getString(R.string.label_scan_failed_message))
                flattened = null
                analyzer.paused = false
            }
        }
        return true
    }

    fun dismissError() {
        state = LabelScanState.Ready
    }

    private fun savePhoto(bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "labels").apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}.jpg").apply {
            outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }
}
