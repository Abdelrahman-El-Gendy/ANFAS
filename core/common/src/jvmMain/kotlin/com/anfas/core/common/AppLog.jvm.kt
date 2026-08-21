package com.anfas.core.common

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import java.io.File
import java.time.Instant

/**
 * Desktop is the one platform where logging needs real work. Kermit's JVM writer targets stdout,
 * and a packaged app has no terminal attached — `windows { console = false }` and a macOS `.app`
 * bundle both swallow it — so without a file sink a desktop crash leaves nothing behind and the
 * user has nothing to send.
 */
internal actual fun installPlatformLogSinks() {
    val sink = runCatching { FileLogWriter(desktopLogFile()) }.getOrNull() ?: return
    Logger.setLogWriters(platformLogWriter(), sink)
}

/** Where the desktop log lives, so a crash dialog can tell the user what to send. */
fun desktopLogPath(): String = runCatching { desktopLogFile().absolutePath }.getOrElse { "unknown" }

/**
 * OS-idiomatic log location. Deliberately not `~/.anfas`: macOS has a standard log directory
 * that Console.app indexes, and a dot-directory in the Windows profile can end up synced to
 * OneDrive.
 */
internal fun desktopLogFile(
    osName: String = System.getProperty("os.name").orEmpty(),
    userHome: String = System.getProperty("user.home").orEmpty(),
    localAppData: String? = System.getenv("LOCALAPPDATA"),
    xdgStateHome: String? = System.getenv("XDG_STATE_HOME"),
): File {
    val os = osName.lowercase()
    val dir = when {
        os.contains("mac") || os.contains("darwin") ->
            File(userHome, "Library/Logs/ANFAS")

        os.contains("win") ->
            File(localAppData ?: File(userHome, "AppData/Local").path, "ANFAS/logs")

        else ->
            File(xdgStateHome ?: File(userHome, ".local/state").path, "anfas/logs")
    }
    dir.mkdirs()
    return File(dir, "anfas.log")
}

/**
 * Append-only writer with a crude two-file rotation. Deliberately not logback: that is a
 * `:server` dependency and would need XML config shipped inside the app image.
 */
private class FileLogWriter(private val file: File) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        runCatching {
            rotateIfNeeded()
            val line = buildString {
                append(Instant.now()).append(' ')
                append(severity.name.first()).append('/').append(tag).append(": ")
                append(message)
                if (throwable != null) {
                    append('\n').append(throwable.stackTraceToString())
                }
                append('\n')
            }
            file.appendText(line)
        }
        // A failing logger must never take down the app, so write errors are swallowed here.
    }

    private fun rotateIfNeeded() {
        if (file.length() < MAX_BYTES) return
        val previous = File(file.parentFile, file.name + ".1")
        if (previous.exists()) previous.delete()
        file.renameTo(previous)
    }

    private companion object {
        const val MAX_BYTES = 1L * 1024 * 1024
    }
}
