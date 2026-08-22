package com.anfas.core.ocr

import androidx.compose.runtime.Composable
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.model.OcrLine
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop has no capture and no OCR, deliberately and visibly.
 *
 * Three reasons, weakest to strongest. There is no viable pure-JVM engine: Tess4J means bundling
 * JNI natives plus tens of megabytes of trained data into every Dmg/Msi/Deb and signing three
 * platform-native artifacts. A file-picker WITHOUT recognition would be worse than nothing — a
 * batch whose five columns are all blank is a hand-typing form disguised as a scan, and if hand
 * entry is wanted the answer is an "add member" form, which the design already has. And
 * decisively: the database is a local file per device with no sync yet, so a sheet photographed
 * on the reception phone will never appear here regardless.
 *
 * The desktop build keeps the wide 40/60 review pane, which is genuinely the best place to review
 * a sheet once sync exists.
 */
actual val ocrCapability: OcrCapability = OcrCapability(
    canCapture = false,
    canPickImage = false,
    canRecogniseText = false,
)

actual suspend fun requestCameraAccess(): CameraAccess = CameraAccess.NotRequired

actual fun openAppSettings() = Unit

@Composable
actual fun rememberImageSource(onResult: (AppResult<CapturedImage>) -> Unit): ImageSource =
    object : ImageSource {
        override fun captureFromCamera() = onResult(unsupported())
        override fun pickFromLibrary() = onResult(unsupported())
    }

private fun unsupported(): AppResult<CapturedImage> = AppResult.Failure(
    AppError.Unexpected("Capture is not available on desktop; scan sheets on the phone app."),
)

internal actual fun platformOcrModule(): Module = module {
    single<TextRecogniser> { UnsupportedTextRecogniser }
    single<IntakeImageStore> { NoOpIntakeImageStore }
}

private object UnsupportedTextRecogniser : TextRecogniser {
    override suspend fun recognise(image: CapturedImage): AppResult<List<OcrLine>> =
        AppResult.Failure(AppError.Unexpected("Text recognition is not available on desktop."))

    override suspend fun supportsArabicScript(): Boolean = false
}

/** Nothing is ever captured here, so there is nothing to clean up. */
private object NoOpIntakeImageStore : IntakeImageStore {
    override suspend fun delete(uri: String) = Unit
    override suspend fun purgeExcept(keep: Set<String>) = Unit
}
