package dev.southstar21.kaloria.core.scanner.label

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A scanned label waiting for review, with the photo it was read from. */
data class LabelDraft(val extraction: LabelExtraction, val photo: File?, val barcode: String?)

/**
 * Hands a scan result from the camera screen to the food editor. Drafts are large and
 * short-lived, so they're kept in memory and referenced from the navigation key by id.
 */
@Singleton
class ScanDraftStore @Inject constructor() {
    private val drafts = ConcurrentHashMap<String, LabelDraft>()

    fun put(draft: LabelDraft): String = UUID.randomUUID().toString().also { drafts[it] = draft }

    operator fun get(id: String): LabelDraft? = drafts[id]

    fun remove(id: String) {
        drafts.remove(id)?.photo?.delete()
    }
}
