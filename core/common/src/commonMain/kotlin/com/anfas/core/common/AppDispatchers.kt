package com.anfas.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `Dispatchers.IO` does not exist in the common source set — it is declared per platform.
 * Everything that needs a dispatcher takes this interface so tests can substitute a
 * deterministic one instead of reaching for the global object.
 */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher get() = platformIoDispatcher
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val main: CoroutineDispatcher get() = Dispatchers.Main
}

internal expect val platformIoDispatcher: CoroutineDispatcher
