package io.strlght.estee.util

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] variant that preserves structured concurrency: a [CancellationException] is
 * rethrown instead of being captured into the [Result], so cancelled coroutines still die.
 *
 * Inline and non-suspend on purpose: the block is inlined at the call site, which allows
 * passing suspend calls to it, the same way kotlinx.coroutines' internal runSuspendCatching works.
 */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
