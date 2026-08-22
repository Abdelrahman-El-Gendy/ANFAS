package com.anfas.core.ocr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.model.OcrBounds
import com.anfas.core.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import kotlin.coroutines.resume

actual val ocrCapability: OcrCapability = OcrCapability(
    canCapture = true,
    canPickImage = true,
    canRecogniseText = true,
)

/**
 * Nothing to request. See the comment in androidMain/AndroidManifest.xml: ACTION_IMAGE_CAPTURE
 * needs no permission as long as CAMERA is never declared, so there is no denied state to
 * recover from and the design's permission-denied screen is iOS-only.
 */
actual suspend fun requestCameraAccess(): CameraAccess = CameraAccess.NotRequired

private var appSettingsOpener: (() -> Unit)? = null

actual fun openAppSettings() {
    appSettingsOpener?.invoke()
}

internal actual fun platformOcrModule(): Module = module {
    single<TextRecogniser> { MlKitTextRecogniser(androidContext(), get()) }
    single<IntakeImageStore> { AndroidIntakeImageStore(androidContext(), get()) }
    // openAppSettings() is a plain top-level function so common code can call it without a
    // Context, which means the Context has to reach it some other way. Bound once at graph
    // construction rather than held statically from an Activity, which would leak it.
    single(createdAtStart = true) {
        val context = androidContext()
        appSettingsOpener = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
        Unit
    }
}

/** Where captured sheets live. Internal cache — see res/xml/intake_file_paths.xml. */
internal class IntakeCaptureFiles(private val context: Context) {
    val directory: File get() = File(context.cacheDir, "intake").apply { mkdirs() }

    fun authority(): String = "${context.packageName}.intake.fileprovider"
}

private class MlKitTextRecogniser(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : TextRecogniser {

    private val log = logger("Ocr")

    // The Latin recogniser specifically. ML Kit offers Latin, Chinese, Devanagari, Japanese and
    // Korean -- there is no Arabic model, which is the limitation TextRecogniser documents.
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognise(image: CapturedImage): AppResult<List<OcrLine>> =
        withContext(dispatchers.io) {
            runCatching {
                awaitText(InputImage.fromFilePath(context, Uri.parse(image.uri)))
            }.fold(
                onSuccess = { text -> AppResult.Success(text.toOcrLines(image)) },
                onFailure = { cause ->
                    log.e("Text recognition failed", cause)
                    AppResult.Failure(AppError.Unexpected("Could not read the sheet"))
                },
            )
        }

    /**
     * ML Kit's supported-language set is fixed per model and not queryable, unlike Vision's.
     * Hardcoding false is therefore accurate rather than pessimistic: there is no Arabic model
     * to gain.
     */
    override suspend fun supportsArabicScript(): Boolean = false

    private suspend fun awaitText(input: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            client.process(input)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.cancel(it) }
        }
}

/**
 * ML Kit gives pixel rectangles; [OcrLine] is normalised 0..1 with a top-left origin, which is
 * already ML Kit's convention — so this only divides, with no axis flip (contrast the iOS side,
 * where Vision's bottom-left origin has to be inverted).
 */
private fun Text.toOcrLines(image: CapturedImage): List<OcrLine> {
    val width = image.widthPx.toFloat().takeIf { it > 0f } ?: return emptyList()
    val height = image.heightPx.toFloat().takeIf { it > 0f } ?: return emptyList()

    return textBlocks.flatMap { it.lines }.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        OcrLine(
            text = line.text,
            confidence = line.lineConfidence(),
            bounds = OcrBounds.normalised(
                left = box.left / width,
                top = box.top / height,
                right = box.right / width,
                bottom = box.bottom / height,
            ),
        )
    }
}

/**
 * Isolated in one place on purpose. `Text.Line.getConfidence()` is annotated as returning a
 * float but is documented to be populated only for some models, and returns NaN otherwise.
 * A NaN would propagate silently into IntakeField.needsReview and make every cell look
 * suspicious, so an unusable value becomes 1f — "no reason to doubt this" — and the parser's
 * own heuristics carry the review decision instead.
 */
private fun Text.Line.lineConfidence(): Float {
    val reported = runCatching { confidence }.getOrNull() ?: return 1f
    return if (reported.isNaN()) 1f else reported.coerceIn(0f, 1f)
}

private class AndroidIntakeImageStore(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : IntakeImageStore {

    private val log = logger("Ocr")

    override suspend fun delete(uri: String) = withContext(dispatchers.io) {
        runCatching { uri.toLocalFile(context)?.delete() }
            .onFailure { log.w("Could not delete a captured sheet", it) }
        Unit
    }

    override suspend fun purgeExcept(keep: Set<String>) = withContext(dispatchers.io) {
        val kept = keep.mapNotNull { it.toLocalFile(context)?.absolutePath }.toSet()
        File(context.cacheDir, "intake").listFiles().orEmpty()
            .filter { it.absolutePath !in kept }
            .forEach { stale ->
                runCatching { stale.delete() }
                    .onFailure { log.w("Could not purge a stale sheet", it) }
            }
        Unit
    }
}

/**
 * Only ever deletes inside our own intake directory. A batch row's uri comes from the database,
 * and treating it as an arbitrary deletable path would turn a corrupt row into data loss
 * elsewhere on disk.
 */
private fun String.toLocalFile(context: Context): File? {
    val path = Uri.parse(this).path ?: return null
    val file = File(path).canonicalFile
    val root = File(context.cacheDir, "intake").canonicalFile
    return file.takeIf { it.path.startsWith(root.path + File.separator) }
}
