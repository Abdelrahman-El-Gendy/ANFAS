package com.anfas.feature.intakeocr

import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.model.OcrLine
import com.anfas.core.ocr.CameraAccess
import com.anfas.core.ocr.CameraPermissions
import com.anfas.core.ocr.CapturedImage
import com.anfas.core.ocr.IntakeImageStore
import com.anfas.core.ocr.TextRecogniser

/** Returns whatever the test hands it, so ingestion can be exercised without a camera. */
internal class FakeTextRecogniser(
    private val result: AppResult<List<OcrLine>> = AppResult.Success(emptyList()),
) : TextRecogniser {
    var calls = 0
        private set

    override suspend fun recognise(image: CapturedImage): AppResult<List<OcrLine>> {
        calls++
        return result
    }

    override suspend fun supportsArabicScript(): Boolean = false
}

/** Records deletions, which is the only way to assert the image-cleanup contract. */
internal class RecordingImageStore : IntakeImageStore {
    val deleted = mutableListOf<String>()
    val purgedKeeping = mutableListOf<Set<String>>()

    override suspend fun delete(uri: String) {
        deleted += uri
    }

    override suspend fun purgeExcept(keep: Set<String>) {
        purgedKeeping += keep
    }
}

internal fun capturedImage(uri: String = "file://sheet.jpg") =
    CapturedImage(uri = uri, widthPx = 1000, heightPx = 1400)

internal fun storageFailure(message: String = "disk full") =
    AppResult.Failure(AppError.Storage(message))

/** Lets a test drive the Denied branch, which the platform expect-fun made unreachable. */
internal class FakeCameraPermissions(private val access: CameraAccess = CameraAccess.NotRequired) :
    CameraPermissions {
    var settingsOpened = 0
        private set

    override suspend fun request(): CameraAccess = access

    override fun openSettings() {
        settingsOpened++
    }
}
