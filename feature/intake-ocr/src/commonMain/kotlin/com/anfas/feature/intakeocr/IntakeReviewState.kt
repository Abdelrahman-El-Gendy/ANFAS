package com.anfas.feature.intakeocr

import com.anfas.core.data.ImportOutcome
import com.anfas.core.model.IntakeBatch

/**
 * The batch-review screen.
 *
 * [NoBatches] and [Loaded] are separate cases because they are entirely different screens in
 * the export — `ocr-intake-empty` is a centred invitation, `ocr-intake-review` is a split
 * source-and-table view.
 */
sealed interface IntakeReviewContent {
    data object Loading : IntakeReviewContent

    /** No sheet has been scanned, or the last one was imported or discarded. */
    data object NoBatches : IntakeReviewContent

    data class Loaded(val batch: IntakeBatch) : IntakeReviewContent

    data class Failed(val message: String) : IntakeReviewContent
}

/**
 * [zoom] and [pan] live in UI state rather than being remembered inside the composable so the
 * source view keeps its position across a recomposition caused by an edit two columns away.
 *
 * [outcome] is set once an import completes, so the screen can report what happened before the
 * batch disappears from the review list.
 */
data class IntakeReviewState(
    val content: IntakeReviewContent = IntakeReviewContent.Loading,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val isImporting: Boolean = false,
    val outcome: ImportOutcome? = null,
    val notice: String? = null,
) {
    val batch: IntakeBatch? get() = (content as? IntakeReviewContent.Loaded)?.batch

    val readyCount: Int get() = batch?.importableRows?.size ?: 0
    val totalCount: Int get() = batch?.rows?.size ?: 0

    val canImport: Boolean get() = batch?.canImport == true && !isImporting

    companion object {
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4f
    }
}
