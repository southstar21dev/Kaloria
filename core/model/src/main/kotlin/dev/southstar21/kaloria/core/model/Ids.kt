package dev.southstar21.kaloria.core.model

import java.security.SecureRandom

/**
 * Record ids are generated on the device in PocketBase's own format (15 chars of `[a-z0-9]`), so a
 * row created offline keeps the same id locally and on the server. 36^15 ≈ 2^77 values.
 */
object Ids {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private const val LENGTH = 15
    private val random = SecureRandom()

    fun newId(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
