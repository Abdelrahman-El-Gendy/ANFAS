package com.anfas.core.common

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Logging seam. Call sites depend on this, not on Kermit, so the backend can change without
 * touching every module — the same reason [AppDispatchers] exists.
 *
 * Kermit was already declared in five build files and imported in none. It is kept rather than
 * removed because the app is about to ship to three platforms with no crash reporter, and the
 * minimum viable diagnostic is a log someone can actually read.
 *
 * **Never log member names or phone numbers.** The whole domain is PII, and on desktop these
 * lines land in a plaintext file on disk. Log ids and counts instead.
 */
interface AppLogger {
    fun v(message: String)
    fun d(message: String)
    fun i(message: String)
    fun w(message: String, throwable: Throwable? = null)
    fun e(message: String, throwable: Throwable? = null)
}

/** One logger per subsystem; [tag] shows up in logcat/os_log/the desktop log file. */
fun logger(tag: String): AppLogger = KermitLogger(Logger.withTag(tag))

private class KermitLogger(private val delegate: Logger) : AppLogger {
    override fun v(message: String) = delegate.v { message }
    override fun d(message: String) = delegate.d { message }
    override fun i(message: String) = delegate.i { message }

    override fun w(message: String, throwable: Throwable?) =
        if (throwable == null) delegate.w { message } else delegate.w(throwable) { message }

    override fun e(message: String, throwable: Throwable?) =
        if (throwable == null) delegate.e { message } else delegate.e(throwable) { message }
}

/**
 * Called once per launcher at startup.
 *
 * [verbose] should be true only for debuggable builds. Each launcher decides how it knows that,
 * because the answer differs per platform: Android reads its own ApplicationInfo flags (AGP 9
 * has BuildConfig off by default, and turning it on for one boolean is not worth it), iOS asks
 * the Kotlin/Native runtime, and desktop uses a system property.
 *
 * No Kermit type appears in this signature on purpose — that would put Kermit in this module's
 * ABI and force it from `implementation` to `api`, defeating the point of the seam. Platform
 * sinks are added by [installPlatformLogSinks] instead.
 */
fun configureLogging(verbose: Boolean) {
    Logger.setMinSeverity(if (verbose) Severity.Verbose else Severity.Info)
    installPlatformLogSinks()
}

/**
 * Adds any sink the platform needs beyond Kermit's default. Android and iOS already write to
 * logcat and os_log, so their actuals do nothing; desktop needs a file, because Kermit's JVM
 * default writes to stdout and a packaged jpackage app has no terminal attached.
 */
internal expect fun installPlatformLogSinks()

/**
 * Safety net for a component's or repository's coroutine scope.
 *
 * Two scopes in this app were previously unprotected: the plan-catalogue seeder in
 * `:core:data`'s Koin module (a DB failure there crashed the app at launch), and every feature
 * component's `stateIn` over a Room Flow (which crashed on Android and died silently on iOS).
 *
 * This is a net, not a fix. It stops the crash but leaves the screen frozen, so a component that
 * can fail should still `catch` in its flow chain and surface an error state. Use both.
 */
fun appExceptionHandler(tag: String): kotlinx.coroutines.CoroutineExceptionHandler =
    kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        logger(tag).e("Unhandled coroutine failure", throwable)
    }
