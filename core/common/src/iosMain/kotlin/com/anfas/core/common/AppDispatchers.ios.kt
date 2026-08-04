package com.anfas.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * `Dispatchers.IO` is `internal` in kotlinx-coroutines 1.11.0 on Kotlin/Native — it is public
 * API only on JVM/Android. Native falls back to the Default pool.
 */
internal actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.Default
