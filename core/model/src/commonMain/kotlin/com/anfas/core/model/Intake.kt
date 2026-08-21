package com.anfas.core.model

import kotlin.time.Instant

/**
 * A photographed sign-up sheet and everything OCR read off it.
 *
 * A batch is a *review* artefact, not member data: nothing here is trusted until a human has
 * looked at it and pressed import. That is why every field keeps its raw OCR text rather than a
 * parsed value — a phone number the engine read as "555-O192" has to be shown back exactly as
 * misread so staff can see what to fix.
 */
data class IntakeBatch(
    val id: IntakeBatchId,
    val capturedAt: Instant,
    /** Where the source photograph lives. Null once the image has been cleaned up. */
    val sourceImageUri: String?,
    val status: IntakeBatchStatus,
    val rows: List<IntakeRow>,
) {
    /** Rows that can be imported right now — the "6 of 8 rows ready" counter. */
    val importableRows: List<IntakeRow> get() = rows.filter { it.isImportable }

    val blockedRows: List<IntakeRow> get() = rows.filterNot { it.isImportable }

    /** Rows that are importable but still carry something a human should glance at. */
    val rowsNeedingReview: List<IntakeRow> get() = rows.filter { it.isImportable && it.needsReview }

    val canImport: Boolean
        get() = status == IntakeBatchStatus.REVIEWING && importableRows.isNotEmpty()
}

enum class IntakeBatchStatus {
    /** Parsed and awaiting a human. */
    REVIEWING,
    IMPORTED,
    DISCARDED,
}

/**
 * One line off the sheet.
 *
 * [ordinal] is the number printed in the "#" column — the row's position on the paper, which
 * is how staff cross-reference the scan against the photograph. It is not a database index and
 * does not change when rows are filtered.
 */
data class IntakeRow(
    val id: IntakeRowId,
    val ordinal: Int,
    val name: IntakeField,
    val phone: IntakeField,
    val startDate: IntakeField,
    val endDate: IntakeField,
    val plan: IntakeField,
    val issues: Set<IntakeIssue>,
    /** Where this row sits on the source image, for the overlay boxes. Null if unknown. */
    val bounds: OcrBounds?,
) {
    val fields: List<IntakeField> get() = listOf(name, phone, startDate, endDate, plan)

    /**
     * Blocking issues stop an import; advisory ones do not. That distinction is what makes
     * "6 of 8 ready" possible — a row with a low-confidence plan still imports, a row whose
     * phone duplicates an existing member does not.
     */
    val blockingIssues: Set<IntakeIssue> get() = issues.filter { it.isBlocking }.toSet()

    val isImportable: Boolean get() = blockingIssues.isEmpty()

    /** Draws the amber review marker: an advisory issue, or a field OCR was unsure about. */
    val needsReview: Boolean
        get() = issues.any { !it.isBlocking } || fields.any { it.needsReview }
}

/**
 * A single OCR'd cell.
 *
 * [confidence] is the engine's own score, 0..1. [wasEdited] outranks it: once a human has
 * typed a value, the machine's doubt is no longer interesting and the review marker clears.
 */
data class IntakeField(
    val value: String,
    val confidence: Float,
    val wasEdited: Boolean = false,
) {
    val isBlank: Boolean get() = value.isBlank()

    val needsReview: Boolean get() = !wasEdited && confidence < REVIEW_THRESHOLD

    fun editedTo(newValue: String): IntakeField =
        copy(value = newValue, wasEdited = true)

    companion object {
        /**
         * Below this, a cell gets the amber marker and an editable control. Chosen to be
         * deliberately cautious — a wrong phone number means a member never gets reminders,
         * and the cost of an unnecessary glance is far lower.
         */
        const val REVIEW_THRESHOLD = 0.85f

        /** For values a human supplied rather than OCR. */
        fun entered(value: String) = IntakeField(value, confidence = 1f, wasEdited = true)
    }
}

/**
 * Everything that can be wrong with a row.
 *
 * [isBlocking] is the important property. The export shows 8 rows with "6 of 8 rows ready for
 * import": a duplicate phone and a missing required field stop a row, while a low-confidence
 * cell only asks for a look.
 */
enum class IntakeIssue(val label: String, val isBlocking: Boolean) {
    MISSING_NAME("Name missing", isBlocking = true),
    MISSING_PHONE("Phone missing", isBlocking = true),
    DUPLICATE_PHONE("Duplicate", isBlocking = true),
    DUPLICATE_IN_BATCH("Duplicate in this sheet", isBlocking = true),
    UNREADABLE_DATE("Date unreadable", isBlocking = false),
    END_BEFORE_START("End date before start", isBlocking = true),
    UNKNOWN_PLAN("Plan not recognised", isBlocking = false),
    LOW_CONFIDENCE("Check this row", isBlocking = false),
}

/**
 * Normalised rectangle on the source image, 0..1 on both axes, so the overlay survives any
 * zoom level or render size without carrying pixel dimensions around.
 */
data class OcrBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && right in 0f..1f && top in 0f..1f && bottom in 0f..1f) {
            "OcrBounds must be normalised to 0..1, got ($left, $top, $right, $bottom)"
        }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
