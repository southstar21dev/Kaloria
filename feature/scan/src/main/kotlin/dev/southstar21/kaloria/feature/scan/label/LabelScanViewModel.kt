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
import dev.southstar21.kaloria.core.scanner.label.LabelDraft
import dev.southstar21.kaloria.core.scanner.label.LabelScanner
import dev.southstar21.kaloria.core.scanner.label.NanoStatus
import dev.southstar21.kaloria.core.scanner.label.ScanDraftStore
import dev.southstar21.kaloria.feature.scan.LabelScanKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: LabelScanKey): LabelScanViewModel
    }

    var state by mutableStateOf<LabelScanState>(LabelScanState.Ready)
        private set

    var nano by mutableStateOf<NanoStatus?>(null)
        private set

    /** Skip Gemini Nano and use only the text rules, to compare the two. */
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

    fun read(bitmap: Bitmap) {
        if (state == LabelScanState.Reading) return
        state = LabelScanState.Reading
        viewModelScope.launch {
            runCatching {
                val extraction = scanner.scan(bitmap, forceRules = rulesOnly)
                if (!extraction.isUsable) error("No nutrition table found. Fill the frame with the table and try again.")
                val photo = withContext(Dispatchers.IO) { savePhoto(bitmap) }
                drafts.put(LabelDraft(extraction, photo, key.barcode))
            }.onSuccess { id ->
                state = LabelScanState.Ready
                draftChannel.send(id)
            }.onFailure {
                state = LabelScanState.Failed(it.message ?: "Couldn't read the label.")
            }
        }
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
