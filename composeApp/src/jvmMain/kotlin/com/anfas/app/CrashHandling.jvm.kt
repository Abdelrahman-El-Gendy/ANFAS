package com.anfas.app

import com.anfas.core.common.desktopLogPath
import com.anfas.core.common.logger
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

/**
 * Desktop is the only platform where the user has no other way to report anything, so as well as
 * logging we tell them where the log file is. Without this a desktop crash is completely silent:
 * the window vanishes and a packaged app has no console attached.
 */
actual fun installCrashHandler() {
    val log = logger("Crash")
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        log.e("Uncaught exception on ${thread.name}", throwable)
        runCatching {
            SwingUtilities.invokeLater {
                JOptionPane.showMessageDialog(
                    null,
                    "ANFAS hit an unexpected error and has to close.\n\n" +
                        "${throwable::class.simpleName}: ${throwable.message}\n\n" +
                        "A log was written to:\n${desktopLogPath()}",
                    "ANFAS",
                    JOptionPane.ERROR_MESSAGE,
                )
            }
        }
        exitProcess(1)
    }
}
