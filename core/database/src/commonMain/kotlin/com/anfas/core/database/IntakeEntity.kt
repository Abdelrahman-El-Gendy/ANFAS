package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A photographed sign-up sheet awaiting review.
 *
 * The image itself is not stored in the database — only a URI to it. Sheet photographs are
 * megabytes each and would bloat the SQLite file that the whole app reads through.
 */
@Entity(tableName = "intake_batches")
data class IntakeBatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "captured_at_epoch_ms") val capturedAtEpochMs: Long,
    @ColumnInfo(name = "source_image_uri") val sourceImageUri: String?,
    val status: String,
)

/**
 * One OCR'd cell: the text, how sure the engine was, and whether a human has since overwritten
 * it. Embedded with a per-column prefix so the five cells of a row stay in one flat table
 * without fifteen hand-named columns.
 */
data class IntakeFieldColumns(
    val value: String,
    val confidence: Float,
    @ColumnInfo(name = "was_edited") val wasEdited: Boolean,
)

/**
 * One line off a sheet.
 *
 * Rows cascade-delete with their batch: a discarded sheet must not leave orphan rows behind,
 * and there is nothing meaningful about a row whose batch is gone.
 *
 * [issues] is a denormalised comma-separated list. It is derived data — recomputed by
 * `IntakeValidator` on every read and every edit — so it is cached here purely so a list
 * screen can show counts without loading and revalidating every batch.
 */
@Entity(
    tableName = "intake_rows",
    foreignKeys = [
        ForeignKey(
            entity = IntakeBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["batch_id", "ordinal"])],
)
data class IntakeRowEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    val ordinal: Int,
    @Embedded(prefix = "name_") val name: IntakeFieldColumns,
    @Embedded(prefix = "phone_") val phone: IntakeFieldColumns,
    @Embedded(prefix = "start_date_") val startDate: IntakeFieldColumns,
    @Embedded(prefix = "end_date_") val endDate: IntakeFieldColumns,
    @Embedded(prefix = "plan_") val plan: IntakeFieldColumns,
    val issues: String,
    @ColumnInfo(name = "bounds_left") val boundsLeft: Float?,
    @ColumnInfo(name = "bounds_top") val boundsTop: Float?,
    @ColumnInfo(name = "bounds_right") val boundsRight: Float?,
    @ColumnInfo(name = "bounds_bottom") val boundsBottom: Float?,
)

@Dao
interface IntakeDao {

    /** Newest first — staff work the sheet they just photographed. */
    @Query("SELECT * FROM intake_batches ORDER BY captured_at_epoch_ms DESC")
    fun observeBatches(): Flow<List<IntakeBatchEntity>>

    @Query("SELECT * FROM intake_batches WHERE status = :status ORDER BY captured_at_epoch_ms DESC")
    fun observeBatchesWithStatus(status: String): Flow<List<IntakeBatchEntity>>

    @Query("SELECT * FROM intake_batches WHERE id = :id")
    fun observeBatch(id: String): Flow<IntakeBatchEntity?>

    @Query("SELECT * FROM intake_rows WHERE batch_id = :batchId ORDER BY ordinal ASC")
    fun observeRows(batchId: String): Flow<List<IntakeRowEntity>>

    @Query("SELECT * FROM intake_rows WHERE batch_id = :batchId ORDER BY ordinal ASC")
    suspend fun rowsOnce(batchId: String): List<IntakeRowEntity>

    /** Single row by id, for applying one cell edit without loading its whole sheet. */
    @Query("SELECT * FROM intake_rows WHERE id = :id")
    suspend fun rowOnce(id: String): IntakeRowEntity?

    @Upsert
    suspend fun upsertBatch(batch: IntakeBatchEntity)

    @Upsert
    suspend fun upsertRows(rows: List<IntakeRowEntity>)

    /**
     * Writes a batch and its rows together, so a reader can never observe a batch whose rows
     * have not landed yet.
     */
    @Transaction
    suspend fun upsertBatchWithRows(batch: IntakeBatchEntity, rows: List<IntakeRowEntity>) {
        upsertBatch(batch)
        upsertRows(rows)
    }

    @Query("UPDATE intake_batches SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM intake_batches WHERE id = :id")
    suspend fun deleteBatch(id: String)
}
