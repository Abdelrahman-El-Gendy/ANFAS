@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anfas.core.ocr

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.model.OcrLine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIImage
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import kotlin.coroutines.resume

actual val ocrCapability: OcrCapability = OcrCapability(
    canCapture = true,
    canPickImage = true,
    canRecogniseText = true,
)

actual suspend fun requestCameraAccess(): CameraAccess {
    // Distinguishing Denied from NotYetAsked is the only reason this exists: a denied user must be
    // routed to Settings, and a not-yet-asked one must see the system prompt. That is ~30 lines,
    // versus a permissions library with its own Compose and lifecycle integration to keep
    // compatible with Compose 1.11.1.
    return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> CameraAccess.Granted

        AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { continuation ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                continuation.resume(if (granted) CameraAccess.Granted else CameraAccess.Denied)
            }
        }

        else -> CameraAccess.Denied
    }
}

actual fun openAppSettings() {
    val url = NSURL(string = UIApplicationOpenSettingsURLString)
    UIApplication.sharedApplication.openURL(url)
}

internal actual fun platformOcrModule(): Module = module {
    single<TextRecogniser> { VisionTextRecogniser() }
    single<IntakeImageStore> { AppleIntakeImageStore(get()) }
}

/**
 * Vision-backed recognition.
 *
 * Two settings matter more than they look. `Accurate` because a handwritten sheet under gym
 * lighting needs it. And **`usesLanguageCorrection = false`**, because correction helpfully
 * rewrites phone digits and transliterated names into dictionary words — catastrophic when the
 * whole point is reading exactly what was written.
 */
internal class VisionTextRecogniser : TextRecogniser {

    override suspend fun recognise(image: CapturedImage): AppResult<List<OcrLine>> {
        val uiImage = UIImage.imageWithContentsOfFile(image.uri.removePrefix("file://"))
            ?: return AppResult.Failure(AppError.Unexpected("Could not read ${image.uri}"))
        val cgImage = uiImage.CGImage
            ?: return AppResult.Failure(AppError.Unexpected("Image has no bitmap representation"))

        return suspendCancellableCoroutine { continuation ->
            val request = VNRecognizeTextRequest { request, error ->
                if (error != null) {
                    continuation.resume(
                        AppResult.Failure(
                            AppError.Unexpected(
                                "Text recognition failed: ${error.localizedDescription}",
                            ),
                        ),
                    )
                    return@VNRecognizeTextRequest
                }
                val lines = request?.results
                    ?.filterIsInstance<VNRecognizedTextObservation>()
                    ?.mapNotNull { observation -> observation.toOcrLine() }
                    .orEmpty()
                continuation.resume(AppResult.Success(lines))
            }
            request.setRecognitionLevel(VNRequestTextRecognitionLevelAccurate)
            request.setUsesLanguageCorrection(false)

            val handler = VNImageRequestHandler(cgImage, mapOf<Any?, Any?>())
            runCatching { handler.performRequests(listOf(request), null) }
                .onFailure {
                    continuation.resume(
                        AppResult.Failure(AppError.Unexpected("Text recognition failed")),
                    )
                }
        }
    }

    /**
     * Queried at runtime rather than hardcoded, so the feature improves silently if a future iOS
     * adds Arabic. Through iOS 17 it does not.
     */
    override suspend fun supportsArabicScript(): Boolean = runCatching {
        VNRecognizeTextRequest(completionHandler = null)
            .supportedRecognitionLanguagesAndReturnError(null)
            ?.any { (it as? String)?.startsWith("ar") == true } == true
    }.getOrDefault(false)
}

private fun VNRecognizedTextObservation.toOcrLine(): OcrLine? {
    val candidate =
        topCandidates(1u).firstOrNull() as? platform.Vision.VNRecognizedText ?: return null
    val text = candidate.string
    if (text.isBlank()) return null
    // Read the CGRect once; each useContents call is a separate memory scope.
    val rect = boundingBox.useContents {
        VisionRect(origin.x, origin.y, size.width, size.height)
    }
    return OcrLine(
        text = text,
        confidence = candidate.confidence,
        bounds = visionBoxToOcrBounds(
            originX = rect.x,
            originY = rect.y,
            width = rect.width,
            height = rect.height,
        ),
    )
}

/**
 * Sheet photographs live in Application Support and are **excluded from iCloud backup** — they
 * contain member names and phone numbers, and should not ride into a personal backup. The
 * directory comes from [intakeDirectory], the same function the capture path writes through.
 */
internal class AppleIntakeImageStore(private val dispatchers: AppDispatchers) : IntakeImageStore {

    private val log = logger("Ocr")

    override suspend fun delete(uri: String) = withContext(dispatchers.io) {
        val path = uri.toLocalPath() ?: return@withContext
        NSFileManager.defaultManager.removeItemAtPath(path, null)
        Unit
    }

    override suspend fun purgeExcept(keep: Set<String>) = withContext(dispatchers.io) {
        val root = intakeDirectory() ?: return@withContext
        val kept = keep.mapNotNull { it.toLocalPath() }.toSet()
        val manager = NSFileManager.defaultManager
        val names = manager.contentsOfDirectoryAtPath(root, null).orEmpty()
        var purged = 0
        names.forEach { entry ->
            val name = entry as? String ?: return@forEach
            val path = "$root/$name"
            if (path !in kept && manager.removeItemAtPath(path, null)) purged++
        }
        if (purged > 0) log.i("Purged $purged orphaned sheets")
        Unit
    }

    /** Application Support has always been the only capture location here — nothing to adopt. */
    override suspend fun adoptLegacyCaptures(): Map<String, String> = emptyMap()

    /**
     * Confined to our own intake directory, matching Android. A batch row's uri comes from the
     * database, so treating it as an arbitrary removable path would turn one corrupt row into
     * data loss elsewhere in the container — and `purgeExcept` deletes in a loop, which makes
     * the unconfined version considerably worse than the single delete it used to be.
     */
    private fun String.toLocalPath(): String? {
        val root = intakeDirectory() ?: return null
        val path = removePrefix("file://")
        return path.takeIf { it.startsWith("$root/") }
    }
}

private data class VisionRect(val x: Double, val y: Double, val width: Double, val height: Double)
