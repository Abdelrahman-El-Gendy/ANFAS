package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeRowId
import kotlinx.coroutines.flow.Flow

/** Which cell of a row an edit targets. */
enum class IntakeFieldKey { NAME, PHONE, START_DATE, END_DATE, PLAN }

/**
 * What an import actually did.
 *
 * [imported] and [skipped] are reported separately because the numbers differ and staff need
 * to know: a sheet of eight can import six and leave two behind, and a silent "done" would
 * hide the two that still need attention.
 */
data class ImportOutcome(val imported: Int, val skipped: Int)

/**
 * OCR intake batches.
 *
 * Reads are **always validated** before they are emitted: issues are recomputed against the
 * current membership rather than trusted from storage, because a phone number can become a
 * duplicate after the sheet was scanned — someone else registers with it — and a stale
 * "ready to import" flag would let a duplicate member through.
 */
interface IntakeRepository {

    fun observeBatches(): Flow<AppResult<List<IntakeBatch>>>

    /** Emits null when the batch does not exist, so a deleted batch closes its screen. */
    fun observeBatch(id: IntakeBatchId): Flow<AppResult<IntakeBatch?>>

    /**
     * The ingestion seam. An OCR pipeline calls this with what it read off a photograph;
     * nothing else in the app creates batches.
     *
     * There is no OCR engine wired up yet — no camera capture, no text recognition. This
     * exists so the review, edit and import path is complete and testable, and so adding the
     * pipeline later touches nothing above this line.
     */
    suspend fun createBatch(batch: IntakeBatch): AppResult<Unit>

    /** Overwrites one cell and marks it human-entered, clearing its review marker. */
    suspend fun editField(
        rowId: IntakeRowId,
        field: IntakeFieldKey,
        value: String,
    ): AppResult<Unit>

    /**
     * Imports every currently importable row as a member and marks the batch imported.
     *
     * Blocked rows are left behind rather than failing the whole operation — a duplicate on
     * row four must not stop the other seven from being registered.
     */
    suspend fun importBatch(id: IntakeBatchId): AppResult<ImportOutcome>

    suspend fun discardBatch(id: IntakeBatchId): AppResult<Unit>
}
