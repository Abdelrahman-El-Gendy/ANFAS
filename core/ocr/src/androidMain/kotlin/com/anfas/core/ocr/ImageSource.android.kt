package com.anfas.core.ocr

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import java.io.File
import kotlin.uuid.Uuid

/**
 * Capture via the OEM camera app, not CameraX.
 *
 * `TakePicture` needs no runtime permission (see androidMain/AndroidManifest.xml) and hands back
 * a full-resolution file, which is what OCR wants — an in-app viewfinder preview would give us a
 * smaller image, a CAMERA permission to request and a camera UI to reimplement.
 *
 * `PickVisualMedia` is the library fallback: also permission-free, and it needs no `<queries>`
 * entry because the photo picker is a system component.
 */
@Composable
actual fun rememberImageSource(onResult: (AppResult<CapturedImage>) -> Unit): ImageSource {
    val context = LocalContext.current
    // Built from the Context rather than injected: this is a pure function of the Context, and
    // reaching for Koin here would put koin-compose on the module's classpath for one lookup.
    val files = remember(context) { IntakeCaptureFiles(context) }
    // The callback is read through rememberUpdatedState so a recomposition between launching the
    // camera and the result arriving cannot deliver into a stale lambda.
    val callback by rememberUpdatedState(onResult)

    // Holds the URI the camera is writing to. TakePicture's result is a bare Boolean, so the
    // destination has to survive from launch to callback -- and across the process death that a
    // low-memory device can inflict while the camera app is foregrounded, which is why it is
    // rebuilt from a saved file name rather than kept only in memory.
    val pending = remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = pending.value
        pending.value = null
        when {
            !saved -> Unit

            // Cancelled. Not a failure; the caller simply gets nothing.
            file == null || !file.exists() || file.length() == 0L ->
                callback(AppResult.Failure(AppError.Unexpected("The photo was not saved")))

            else -> callback(file.asCapturedImage())
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Copied into our own cache directory so the rest of the pipeline only ever deals with
        // file:// paths it owns and can delete. A content:// URI's permission grant also dies
        // with the activity, which would break re-reading the image after a rotation.
        callback(copyIntoCache(context, uri, files))
    }

    return remember(takePicture, pickImage, files) {
        object : ImageSource {
            override fun captureFromCamera() {
                val file = File(files.directory, "${Uuid.random()}.jpg")
                pending.value = file
                takePicture.launch(
                    FileProvider.getUriForFile(context, files.authority(), file),
                )
            }

            override fun pickFromLibrary() {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}

/**
 * Dimensions come from a bounds-only decode: the parser needs the pixel size to normalise ML
 * Kit's rectangles, and decoding a 12-megapixel sheet into a Bitmap just to read two integers
 * is how a mid-range phone runs out of memory during intake.
 */
private fun File.asCapturedImage(): AppResult<CapturedImage> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) {
        return AppResult.Failure(AppError.Unexpected("The photo could not be read"))
    }
    return AppResult.Success(
        CapturedImage(
            uri = Uri.fromFile(this).toString(),
            widthPx = options.outWidth,
            heightPx = options.outHeight,
        ),
    )
}

private fun copyIntoCache(
    context: android.content.Context,
    source: Uri,
    files: IntakeCaptureFiles,
): AppResult<CapturedImage> = runCatching {
    val target = File(files.directory, "${Uuid.random()}.jpg")
    context.contentResolver.openInputStream(source).use { input ->
        if (input == null) error("No stream for the selected image")
        target.outputStream().use { input.copyTo(it) }
    }
    target
}.fold(
    onSuccess = { it.asCapturedImage() },
    onFailure = { AppResult.Failure(AppError.Unexpected("Could not open the selected image")) },
)
