@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.anfas.core.ocr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import kotlinx.cinterop.useContents
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import kotlin.uuid.Uuid

/**
 * Capture via `UIImagePickerController`, presented from the top-most view controller.
 *
 * Worth revisiting later: `VNDocumentCameraViewController` (VisionKit) is the system document
 * scanner and gives edge detection, perspective correction and contrast normalisation for free.
 * For a paper sheet under fluorescent gym lighting a perspective-corrected scan is worth more to
 * OCR accuracy than any parser tuning — but it is a bigger unknown, and swapping it in later is a
 * one-file change behind this same interface.
 */
@Composable
actual fun rememberImageSource(onResult: (AppResult<CapturedImage>) -> Unit): ImageSource =
    remember(onResult) { UiKitImageSource(onResult) }

private class UiKitImageSource(private val onResult: (AppResult<CapturedImage>) -> Unit) :
    ImageSource {

    override fun captureFromCamera() =
        present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    override fun pickFromLibrary() =
        present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)

    private fun present(source: UIImagePickerControllerSourceType) {
        val host = topViewController()
        if (host == null) {
            onResult(AppResult.Failure(AppError.Unexpected("No view controller to present from")))
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = source
        // Held in a field so the delegate is not collected while the picker is on screen.
        val delegate = PickerDelegate(onResult)
        retained = delegate
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }

    private var retained: PickerDelegate? = null
}

/**
 * Top-level, not a companion field: Kotlin/Native forbids fields on the companion of an ObjC
 * subclass, and `PickerDelegate` extends NSObject.
 */
private const val JPEG_QUALITY = 0.9

private class PickerDelegate(private val onResult: (AppResult<CapturedImage>) -> Unit) :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image == null) {
            onResult(AppResult.Failure(AppError.Unexpected("No image returned")))
            return
        }
        onResult(persist(image))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        // A cancel is not a failure; the caller simply gets nothing.
    }

    private fun persist(image: UIImage): AppResult<CapturedImage> {
        val data: NSData = UIImageJPEGRepresentation(image, JPEG_QUALITY)
            ?: return AppResult.Failure(AppError.Unexpected("Could not encode the photo"))
        val directory = intakeDirectory()
            ?: return AppResult.Failure(AppError.Unexpected("No writable directory"))
        val path = "$directory/${Uuid.random()}.jpg"
        if (!data.writeToFile(path, atomically = true)) {
            return AppResult.Failure(AppError.Unexpected("Could not save the photo"))
        }
        // Sheet photographs contain member PII and must not ride into an iCloud backup.
        NSURL.fileURLWithPath(
            path,
        ).setResourceValue(true, forKey = "NSURLIsExcludedFromBackupKey", error = null)

        val size = image.size.useContents { width to height }
        val scale = image.scale
        return AppResult.Success(
            CapturedImage(
                uri = "file://$path",
                widthPx = (size.first * scale).toInt(),
                heightPx = (size.second * scale).toInt(),
            ),
        )
    }
}

private fun intakeDirectory(): String? {
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: return null
    val directory = "$base/intake"
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return directory
}

/** Walks down through presented controllers, so this works from inside a modal. */
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
