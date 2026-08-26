package com.anfas.core.data

import com.anfas.core.database.IntakeBatchEntity
import com.anfas.core.database.IntakeDao
import com.anfas.core.database.IntakeFieldColumns
import com.anfas.core.database.IntakeRowEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory IntakeDao. Mirrors the real thing where it matters: rows are ordered by ordinal,
 * and deleting a batch cascades to its rows the way the foreign key does.
 */
internal class FakeIntakeDao(
    batches: List<IntakeBatchEntity> = emptyList(),
    rows: List<IntakeRowEntity> = emptyList(),
) : IntakeDao {

    private val batchRows = MutableStateFlow(batches)
    private val rowRows = MutableStateFlow(rows)

    var failure: Throwable? = null

    val currentBatches: List<IntakeBatchEntity> get() = batchRows.value
    val currentRows: List<IntakeRowEntity> get() = rowRows.value

    override fun observeBatches(): Flow<List<IntakeBatchEntity>> = batchRows.map {
        failure?.let { e -> throw e }
        it.sortedByDescending { b -> b.capturedAtEpochMs }
    }

    override fun observeBatchesWithStatus(status: String): Flow<List<IntakeBatchEntity>> =
        batchRows.map { list ->
            failure?.let { throw it }
            list.filter { it.status == status }.sortedByDescending { it.capturedAtEpochMs }
        }

    override fun observeBatch(id: String): Flow<IntakeBatchEntity?> = batchRows.map { list ->
        failure?.let { throw it }
        list.firstOrNull { it.id == id }
    }

    override fun observeRows(batchId: String): Flow<List<IntakeRowEntity>> = rowRows.map { list ->
        failure?.let { throw it }
        list.filter { it.batchId == batchId }.sortedBy { it.ordinal }
    }

    override suspend fun rowsOnce(batchId: String): List<IntakeRowEntity> {
        failure?.let { throw it }
        return rowRows.value.filter { it.batchId == batchId }.sortedBy { it.ordinal }
    }

    override suspend fun rowOnce(id: String): IntakeRowEntity? {
        failure?.let { throw it }
        return rowRows.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertBatch(batch: IntakeBatchEntity) {
        failure?.let { throw it }
        batchRows.value = (batchRows.value.associateBy { it.id } + (batch.id to batch))
            .values.toList()
    }

    override suspend fun upsertRows(rows: List<IntakeRowEntity>) {
        failure?.let { throw it }
        rowRows.value = (rowRows.value.associateBy { it.id } + rows.associateBy { it.id })
            .values.toList()
    }

    override suspend fun setStatus(id: String, status: String) {
        failure?.let { throw it }
        batchRows.value = batchRows.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun deleteBatch(id: String) {
        failure?.let { throw it }
        batchRows.value = batchRows.value.filterNot { it.id == id }
        // The real table has ON DELETE CASCADE.
        rowRows.value = rowRows.value.filterNot { it.batchId == id }
    }

    override suspend fun sourceImageUris(): List<String> {
        failure?.let { throw it }
        return batchRows.value.mapNotNull { it.sourceImageUri }
    }

    override suspend fun relocateSourceImage(from: String, to: String) {
        failure?.let { throw it }
        batchRows.value = batchRows.value.map {
            if (it.sourceImageUri == from) it.copy(sourceImageUri = to) else it
        }
    }
}

internal fun batchEntity(
    id: String = "b1",
    status: String = "REVIEWING",
    capturedAtEpochMs: Long = 1_700_000_000_000,
) = IntakeBatchEntity(
    id = id,
    capturedAtEpochMs = capturedAtEpochMs,
    sourceImageUri = "file://sheet.jpg",
    status = status,
)

internal fun rowEntity(
    id: String,
    batchId: String = "b1",
    ordinal: Int = 1,
    name: String = "Alex Thompson",
    phone: String = "555-0192",
    start: String = "Nov 1, 2023",
    end: String = "Oct 31, 2024",
    plan: String = "Annual",
    confidence: Float = 0.99f,
) = IntakeRowEntity(
    id = id,
    batchId = batchId,
    ordinal = ordinal,
    name = IntakeFieldColumns(name, confidence, false),
    phone = IntakeFieldColumns(phone, confidence, false),
    startDate = IntakeFieldColumns(start, confidence, false),
    endDate = IntakeFieldColumns(end, confidence, false),
    plan = IntakeFieldColumns(plan, confidence, false),
    issues = "",
    boundsLeft = null,
    boundsTop = null,
    boundsRight = null,
    boundsBottom = null,
)
