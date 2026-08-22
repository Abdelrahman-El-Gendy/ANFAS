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
 *
 * [notice] is typed rather than a formatted sentence: a Decompose component cannot read Compose
 * state, so building prose here would hardcode English regardless of the language toggle.
 */
data class IntakeReviewState(
    val content: IntakeReviewContent = IntakeReviewContent.Loading,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val isImporting: Boolean = false,
    /** A capture is being recognised and parsed. Separate from [isImporting]: different wait. */
    val isScanning: Boolean = false,
    /**
     * True once the OS has told us camera access is denied and cannot be re-asked. Sticky for the
     * screen's lifetime rather than a transient notice: the condition does not clear until the
     * user changes it in Settings, so a toast that disappears would leave them stuck.
     */
    val cameraDenied: Boolean = false,
    val outcome: ImportOutcome? = null,
    val notice: IntakeNotice? = null,
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

/** The result of an action, as structure. Rendered by the screen. */
sealed interface IntakeNotice {
    /** Both counts, because naming the leftovers is the whole point. */
    data class Imported(val imported: Int, val skipped: Int) : IntakeNotice
    data object Discarded : IntakeNotice
    data class Failed(val message: String) : IntakeNotice

    /** A capture produced rows. The batch itself arrives through the repository. */
    data class Scanned(val rows: Int) : IntakeNotice

    /** Readable photo, no member rows in it — a blur or a bad angle, so worth saying. */
    data object NothingFound : IntakeNotice
}
