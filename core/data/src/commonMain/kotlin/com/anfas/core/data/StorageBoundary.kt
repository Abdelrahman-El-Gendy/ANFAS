package com.anfas.core.data

import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * The error boundary every repository in this module shares.
 *
 * Cancellation is rethrown rather than folded into a Failure: swallowing it would leave a
 * cancelled coroutine reporting a fake storage error and quietly break structured concurrency.
 */
internal inline fun <T> runStorage(message: String, block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        AppResult.Failure(e.asStorageError(message))
    }

/** Wraps a read Flow so downstream never sees a Room exception. */
internal fun <T, R> Flow<T>.asAppResult(
    message: String,
    transform: (T) -> R,
): Flow<AppResult<R>> =
    map { AppResult.Success(transform(it)) as AppResult<R> }
        .catch { emit(AppResult.Failure(it.asStorageError(message))) }

internal fun Throwable.asStorageError(message: String): AppError =
    AppError.Storage(this.message?.let { "$message: $it" } ?: message)
