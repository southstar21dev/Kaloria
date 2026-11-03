package dev.southstar21.kaloria.core.model

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending code: failures become a [Result], but cancellation is rethrown, so a
 * cancelled caller stops instead of carrying on as if the call had merely failed.
 */
inline fun <R> suspendRunCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
