package com.anfas.app

import com.anfas.core.common.logger

/**
 * Delegates to the previous handler rather than replacing it. Replacing Android's handler
 * outright suppresses the system crash dialog and can leave the process wedged instead of dying
 * cleanly, which is harder to diagnose than the crash itself.
 */
actual fun installCrashHandler() {
    val log = logger("Crash")
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        log.e("Uncaught exception on ${thread.name}", throwable)
        previous?.uncaughtException(thread, throwable)
    }
}
