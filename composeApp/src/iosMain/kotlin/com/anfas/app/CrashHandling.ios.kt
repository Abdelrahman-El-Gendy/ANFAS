package com.anfas.app

import com.anfas.core.common.logger

/**
 * Logs through Kermit — which on iOS lands in os_log and is retrievable from the device — and
 * then lets the runtime terminate so iOS still records a proper crash report. Swallowing the
 * exception would leave Kotlin/Native in an undefined state.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun installCrashHandler() {
    val log = logger("Crash")
    setUnhandledExceptionHook { throwable ->
        log.e("Unhandled exception", throwable)
        terminateWithUnhandledException(throwable)
    }
}
