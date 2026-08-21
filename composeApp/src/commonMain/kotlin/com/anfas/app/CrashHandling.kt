package com.anfas.app

/**
 * Installs a last-resort handler for exceptions nothing else caught.
 *
 * Before this, the app had no global handling on any platform: a repository throwing anywhere
 * under composition went straight to the OS with nothing recorded, and since there is no crash
 * reporter, nothing was recoverable afterwards.
 *
 * Each actual logs through the [com.anfas.core.common.AppLogger] seam and then lets the platform
 * do what it would have done. Deliberately not swallowing: a swallowed failure leaves the app in
 * an undefined state, which is worse than a clean crash with a log line explaining it.
 */
expect fun installCrashHandler()
