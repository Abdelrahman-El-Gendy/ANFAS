package com.anfas.core.ocr

import com.anfas.core.common.AppResult
import com.anfas.core.model.OcrLine

/**
 * A captured photograph on disk.
 *
 * [uri] is always a `file://` URL so one Coil `AsyncImage` call works on every platform.
 */
data class CapturedImage(val uri: String, val widthPx: Int, val heightPx: Int)

/**
 * Reads text off an image. Returns platform-agnostic [OcrLine]s — this interface deliberately
 * knows nothing about IntakeBatch or rows, so it stays reusable for the next thing that needs
 * text off a photo (an ID scan, equipment labels).
 */
interface TextRecogniser {
    suspend fun recognise(image: CapturedImage): AppResult<List<OcrLine>>

    /**
     * Which scripts this platform's engine can actually read, queried at runtime.
     *
     * Worth surfacing rather than assuming: **neither ML Kit nor iOS Vision recognises Arabic
     * script**. ML Kit supports Latin, Chinese, Devanagari, Japanese and Korean with no Arabic
     * option at all; Vision had none through iOS 17. So v1 reads Latin script and Western digits
     * only, even though the app's UI is bilingual. Querying at runtime means the feature improves
     * silently if an OS gains Arabic, instead of hardcoding today's limitation.
     */
    suspend fun supportsArabicScript(): Boolean
}

/**
 * What this platform can do, so the UI reads a flag instead of branching on platform.
 *
 * Desktop is honestly capability-less here: there is no viable pure-JVM OCR engine, a
 * file-picker without recognition would produce a batch of five blank columns (a hand-typing
 * form disguised as a scan), and — decisively — the database is a local file per device with no
 * sync yet, so a sheet photographed on the reception phone would never reach the desktop app
 * anyway.
 */
data class OcrCapability(
    val canCapture: Boolean,
    val canPickImage: Boolean,
    val canRecogniseText: Boolean,
) {
    val isSupported: Boolean get() = canRecogniseText && (canCapture || canPickImage)
}

expect val ocrCapability: OcrCapability

/** Whether the user has granted camera access, and whether we can still ask. */
enum class CameraAccess { Granted, Denied, NotRequired }

expect suspend fun requestCameraAccess(): CameraAccess

/**
 * The camera-permission seam.
 *
 * [requestCameraAccess] and [openAppSettings] are top-level `expect` functions, which makes them
 * unsubstitutable: a test can never exercise the Denied branch, and that branch is the whole
 * reason the denial UI exists. Callers depend on this interface instead — the same reason
 * `AppDispatchers` exists rather than reaching for `Dispatchers.IO` directly.
 */
interface CameraPermissions {
    suspend fun request(): CameraAccess

    /** The only place a user can undo a denial. No-op where there is nothing to deny. */
    fun openSettings()
}

internal object PlatformCameraPermissions : CameraPermissions {
    override suspend fun request(): CameraAccess = requestCameraAccess()

    override fun openSettings() = openAppSettings()
}

/** Opens the OS settings page, for the Denied case the design's permission-denied screen covers. */
expect fun openAppSettings()

/**
 * Housekeeping for captured sheets. These are multi-megabyte photographs of member PII; leaving
 * them behind after import leaks both disk and data.
 */
interface IntakeImageStore {
    suspend fun delete(uri: String)

    /** Startup safety net for images whose batch vanished without cleanup. */
    suspend fun purgeExcept(keep: Set<String>)
}
