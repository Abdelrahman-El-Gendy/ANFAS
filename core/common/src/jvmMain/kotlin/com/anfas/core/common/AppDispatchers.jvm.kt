package com.anfas.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.IO
