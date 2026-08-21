package com.anfas.core.data

import com.anfas.core.database.IntakeBatchEntity
import com.anfas.core.database.IntakeFieldColumns
import com.anfas.core.database.IntakeRowEntity
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeField
import com.anfas.core.model.IntakeIssue
import com.anfas.core.model.IntakeRow
import com.anfas.core.model.IntakeRowId
import com.anfas.core.model.OcrBounds
import kotlin.time.Instant

/**
 * Storage <-> domain for OCR intake.
 *
 * Unknown enum strings degrade the same way as elsewhere: an unrecognised batch status becomes
 * [IntakeBatchStatus.REVIEWING] so the sheet stays visible and fixable, rather than vanishing
 * into a state no screen renders.
 */
internal fun IntakeBatchEntity.toDomain(rows: List<IntakeRow>): IntakeBatch = IntakeBatch(
    id = IntakeBatchId(id),
    capturedAt = Instant.fromEpochMilliseconds(capturedAtEpochMs),
    sourceImageUri = sourceImageUri,
    status = IntakeBatchStatus.entries.firstOrNull { it.name == status }
        ?: IntakeBatchStatus.REVIEWING,
    rows = rows,
)

internal fun IntakeBatch.toEntity(): IntakeBatchEntity = IntakeBatchEntity(
    id = id.value,
    capturedAtEpochMs = capturedAt.toEpochMilliseconds(),
    sourceImageUri = sourceImageUri,
    status = status.name,
)

internal fun IntakeRowEntity.toDomain(): IntakeRow = IntakeRow(
    id = IntakeRowId(id),
    ordinal = ordinal,
    name = name.toDomain(),
    phone = phone.toDomain(),
    startDate = startDate.toDomain(),
    endDate = endDate.toDomain(),
    plan = plan.toDomain(),
    // Cached issues are ignored on read: they are recomputed by IntakeValidator against the
    // current membership, which may have changed since the row was stored.
    issues = decodeIssues(issues),
    bounds = toBounds(),
)

internal fun IntakeRow.toEntity(batchId: IntakeBatchId): IntakeRowEntity = IntakeRowEntity(
    id = id.value,
    batchId = batchId.value,
    ordinal = ordinal,
    name = name.toColumns(),
    phone = phone.toColumns(),
    startDate = startDate.toColumns(),
    endDate = endDate.toColumns(),
    plan = plan.toColumns(),
    issues = issues.joinToString(ISSUE_SEPARATOR) { it.name },
    boundsLeft = bounds?.left,
    boundsTop = bounds?.top,
    boundsRight = bounds?.right,
    boundsBottom = bounds?.bottom,
)

private fun IntakeFieldColumns.toDomain() = IntakeField(
    value = value,
    confidence = confidence,
    wasEdited = wasEdited,
)

private fun IntakeField.toColumns() = IntakeFieldColumns(
    value = value,
    confidence = confidence,
    wasEdited = wasEdited,
)

/**
 * All four corners or none. A half-written box would put a stray overlay rectangle on the
 * source image at coordinates nobody chose.
 */
private fun IntakeRowEntity.toBounds(): OcrBounds? {
    val l = boundsLeft ?: return null
    val t = boundsTop ?: return null
    val r = boundsRight ?: return null
    val b = boundsBottom ?: return null
    return runCatching { OcrBounds(l, t, r, b) }.getOrNull()
}

private fun decodeIssues(encoded: String): Set<IntakeIssue> =
    encoded.split(ISSUE_SEPARATOR)
        .mapNotNull { name -> IntakeIssue.entries.firstOrNull { it.name == name.trim() } }
        .toSet()

private const val ISSUE_SEPARATOR = ","
