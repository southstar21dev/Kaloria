package dev.southstar21.kaloria.core.model

import java.math.BigInteger
import java.security.MessageDigest
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

    /**
     * A valid id derived from [key], an id in another system (e.g. a Health Connect record id). The
     * same key always gives the same id, so two devices importing one record agree on its id.
     */
    fun fromExternal(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        // 256 bits in base 36 is ~50 digits; the first 15 carry ~77 bits, as many as a random id.
        return BigInteger(1, digest).toString(36).padStart(LENGTH, '0').take(LENGTH)
    }
}
