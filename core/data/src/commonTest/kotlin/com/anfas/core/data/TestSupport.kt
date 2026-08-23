package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Unwraps or fails with the error's own message, so a red test says what actually went wrong. */
internal fun <T> AppResult<T>.valueOrFail(): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> throw AssertionError("expected success, got ${error.message}")
}

/**
 * Everything on one dispatcher. These repositories wrap their work in `withContext(io)`, which
 * would otherwise escape runTest's scheduler and make assertions race.
 */
internal object UnconfinedDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}
