package com.anfas.feature.intakeocr

import com.anfas.core.common.AppResult
import com.anfas.core.model.OcrBounds
import com.anfas.core.model.OcrLine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntakeIngestionTest {

    @Test
    fun `a readable sheet becomes a batch under review`() = runTest {
        val repository = FakeIntakeRepository()
        val store = RecordingImageStore()
        val ingestion = IntakeIngestion(
            recogniser = FakeTextRecogniser(AppResult.Success(oneMemberSheet())),
            repository = repository,
            imageStore = store,
        )

        val result = ingestion.ingest(capturedImage())

        assertTrue(result is IngestionResult.Ingested, "got $result")
        assertEquals(1, result.rows)
        assertEquals(
            emptyList(),
            store.deleted,
            "the image is still referenced by the batch and must survive",
        )
    }

    /**
     * The important one. An empty batch would sit at the head of the review queue looking like
     * work, could never be imported, and would have to be discarded by hand — so a blurred
     * photograph must cost one retry, not a cleanup chore.
     */
    @Test
    fun `a sheet with no readable rows creates no batch and deletes the image`() = runTest {
        val repository = FakeIntakeRepository()
        val store = RecordingImageStore()
        val ingestion = IntakeIngestion(
            recogniser = FakeTextRecogniser(AppResult.Success(emptyList())),
            repository = repository,
            imageStore = store,
        )

        val result = ingestion.ingest(capturedImage("file://blurred.jpg"))

        assertEquals(IngestionResult.NothingFound, result)
        assertEquals(listOf("file://blurred.jpg"), store.deleted)
        assertEquals(0, repository.created.size, "no batch may be created")
    }

    @Test
    fun `a recognition failure deletes the image and reports the error`() = runTest {
        val repository = FakeIntakeRepository()
        val store = RecordingImageStore()
        val ingestion = IntakeIngestion(
            recogniser = FakeTextRecogniser(storageFailure("engine unavailable")),
            repository = repository,
            imageStore = store,
        )

        val result = ingestion.ingest(capturedImage("file://broken.jpg"))

        assertTrue(result is IngestionResult.Failed, "got $result")
        assertEquals(listOf("file://broken.jpg"), store.deleted)
        assertEquals(0, repository.created.size)
    }

    /** A batch that never landed leaves an image nothing will ever reference. */
    @Test
    fun `a failed save deletes the image too`() = runTest {
        val repository = FakeIntakeRepository(createResult = storageFailure())
        val store = RecordingImageStore()
        val ingestion = IntakeIngestion(
            recogniser = FakeTextRecogniser(AppResult.Success(oneMemberSheet())),
            repository = repository,
            imageStore = store,
        )

        val result = ingestion.ingest(capturedImage("file://unsaved.jpg"))

        assertTrue(result is IngestionResult.Failed, "got $result")
        assertEquals(listOf("file://unsaved.jpg"), store.deleted)
    }

    @Test
    fun `every parsed row gets a distinct id`() = runTest {
        val repository = FakeIntakeRepository()
        val ingestion = IntakeIngestion(
            recogniser = FakeTextRecogniser(AppResult.Success(twoMemberSheet())),
            repository = repository,
            imageStore = RecordingImageStore(),
        )

        ingestion.ingest(capturedImage())

        val rows = repository.created.single().rows
        assertEquals(2, rows.size)
        assertEquals(
            rows.size,
            rows.map { it.id }.distinct().size,
            "colliding row ids let Room's upsert collapse rows into one",
        )
    }
}

private fun line(text: String, left: Float, top: Float, right: Float, bottom: Float) = OcrLine(
    text = text,
    confidence = 0.9f,
    bounds = OcrBounds.normalised(left, top, right, bottom),
)

/** Header plus one data row, at the column positions the parser's fixtures use. */
private fun oneMemberSheet() = listOf(
    line("#", 0.04f, 0.10f, 0.08f, 0.13f),
    line("Name", 0.14f, 0.10f, 0.30f, 0.13f),
    line("Phone", 0.36f, 0.10f, 0.52f, 0.13f),
    line("Start", 0.56f, 0.10f, 0.68f, 0.13f),
    line("End", 0.72f, 0.10f, 0.82f, 0.13f),
    line("Plan", 0.86f, 0.10f, 0.96f, 0.13f),
    line("1", 0.04f, 0.20f, 0.08f, 0.23f),
    line("Omar Hassan", 0.14f, 0.20f, 0.32f, 0.23f),
    line("01001234567", 0.36f, 0.20f, 0.54f, 0.23f),
    line("Nov 1, 2023", 0.56f, 0.20f, 0.70f, 0.23f),
    line("Dec 1, 2023", 0.72f, 0.20f, 0.84f, 0.23f),
    line("Monthly", 0.86f, 0.20f, 0.97f, 0.23f),
)

private fun twoMemberSheet() = oneMemberSheet() + listOf(
    line("2", 0.04f, 0.30f, 0.08f, 0.33f),
    line("Sara Ali", 0.14f, 0.30f, 0.30f, 0.33f),
    line("01119876543", 0.36f, 0.30f, 0.54f, 0.33f),
    line("Nov 2, 2023", 0.56f, 0.30f, 0.70f, 0.33f),
    line("Dec 2, 2023", 0.72f, 0.30f, 0.84f, 0.33f),
    line("Annual", 0.86f, 0.30f, 0.97f, 0.33f),
)
