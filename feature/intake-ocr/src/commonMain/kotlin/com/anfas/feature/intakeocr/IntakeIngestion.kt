package com.anfas.feature.intakeocr

import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.data.IntakeRepository
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeRowId
import com.anfas.core.model.IntakeSheetParser
import com.anfas.core.ocr.CapturedImage
import com.anfas.core.ocr.IntakeImageStore
import com.anfas.core.ocr.TextRecogniser
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Photograph in, reviewable batch out.
 *
 * The four steps are deliberately separate objects: recognition is platform code, parsing is
 * pure and heavily tested in :core:model, validation happens inside the repository on read, and
 * persistence is the one ingestion seam. This class only sequences them and decides what counts
 * as a failure worth showing someone.
 *
 * It does **not** validate. `IntakeRepository` revalidates on every read, against the current
 * membership — a phone number can become a duplicate after the sheet was photographed, so
 * validating here would just bake in a stale answer.
 */
class IntakeIngestion(
    private val recogniser: TextRecogniser,
    private val repository: IntakeRepository,
    private val imageStore: IntakeImageStore,
) {
    private val log = logger("IntakeIngestion")

    /**
     * Returns the number of rows that reached review.
     *
     * A photograph that yields nothing is reported as [IngestionResult.NothingFound] and the
     * image is deleted rather than stored as an empty batch. An empty batch would sit at the
     * head of the review queue looking like work, could never be imported, and would have to be
     * discarded by hand — a blurred photo should cost one retry, not a cleanup chore.
     */
    suspend fun ingest(image: CapturedImage): IngestionResult {
        val lines = when (val recognised = recogniser.recognise(image)) {
            is AppResult.Failure -> {
                imageStore.delete(image.uri)
                return IngestionResult.Failed(recognised.error)
            }

            is AppResult.Success -> recognised.value
        }

        var nextRow = 0
        val parsed = IntakeSheetParser.parse(
            lines = lines,
            newRowId = { IntakeRowId("${Uuid.random()}-${nextRow++}") },
        )

        log.i(
            "Parsed a sheet: ${lines.size} lines -> ${parsed.rows.size} rows, " +
                "direction=${parsed.direction}, header=${parsed.headerFound}, " +
                "discarded=${parsed.discarded}",
        )

        if (parsed.rows.isEmpty()) {
            imageStore.delete(image.uri)
            return IngestionResult.NothingFound
        }

        val batch = IntakeBatch(
            id = IntakeBatchId(Uuid.random().toString()),
            capturedAt = Clock.System.now(),
            sourceImageUri = image.uri,
            status = IntakeBatchStatus.REVIEWING,
            rows = parsed.rows,
        )

        return when (val saved = repository.createBatch(batch)) {
            is AppResult.Failure -> {
                // The batch never landed, so nothing will ever reference the image.
                imageStore.delete(image.uri)
                IngestionResult.Failed(saved.error)
            }

            is AppResult.Success -> IngestionResult.Ingested(parsed.rows.size)
        }
    }
}

/** Typed, like the screen's other notices, so the UI owns every user-facing sentence. */
sealed interface IngestionResult {
    data class Ingested(val rows: Int) : IngestionResult

    /** The photo was readable but held no member rows — usually a blur or a bad angle. */
    data object NothingFound : IngestionResult

    data class Failed(val error: AppError) : IngestionResult
}
