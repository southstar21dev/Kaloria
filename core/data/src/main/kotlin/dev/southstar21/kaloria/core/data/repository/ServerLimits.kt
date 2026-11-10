package dev.southstar21.kaloria.core.data.repository

/**
 * Text limits of the server's collections (`server/pb_migrations`). The server rejects a longer value
 * on every push, so the row would never sync. Forms hold input to these; saving cuts anything longer
 * that gets past them.
 */
object ServerLimits {
    const val NAME = 200
    const val BRAND = 200
    const val BARCODE = 32
    const val SOURCE_REF = 64
    const val NOTE = 500
}

/**
 * At most [max] UTF-16 units, without splitting a surrogate pair. The server counts characters,
 * of which there are never more than UTF-16 units.
 */
internal fun String.clip(max: Int): String = when {
    length <= max -> this
    this[max - 1].isHighSurrogate() -> substring(0, max - 1)
    else -> substring(0, max)
}
