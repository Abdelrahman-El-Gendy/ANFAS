package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.Insert
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

    /**
     * Every capture still referenced by a batch — the keep-set for
     * `IntakeImageStore.purgeExcept`.
     *
     * One column rather than `observeBatches()`, deliberately: the keep-set needs uris and nothing
     * else, and loading whole batches would pull every parsed cell of every sheet into memory to
     * read one string from each.
     */
    @Query("SELECT source_image_uri FROM intake_batches WHERE source_image_uri IS NOT NULL")
    suspend fun sourceImageUris(): List<String>

    /**
     * Repoints the rows naming a capture that has moved on disk.
     *
     * Matched on the uri rather than on a batch id because the mover is the file system, which
     * knows paths and not batches. Two batches sharing one path is not a state this app can
     * produce (every capture gets a fresh `Uuid`), but if one ever existed both should follow the
     * file, so this deliberately does not restrict to a single row.
     */
    @Query("UPDATE intake_batches SET source_image_uri = :to WHERE source_image_uri = :from")
    suspend fun relocateSourceImage(from: String, to: String)

    // --- sync bookkeeping -------------------------------------------------------------------
    // Declared here, not only on SyncDao, so an outbox entry shares a @Transaction with the write
    // it describes. See SyncOutboxEntity.
    //
    // Note what has deliberately NOT gained a tracked variant: `relocateSourceImage`. It rewrites
    // a device-absolute `file://` path, which means nothing on another device -- pushing it would
    // send a broken path that is non-null, so the receiving review pane would not even fall back
    // to its "no source image" branch. It is local-only maintenance, not a change to shared state,
    // and it is the first concrete instance of the local-only column the design note describes.

    @Insert
    suspend fun recordChange(entry: SyncOutboxEntity)

    @Insert
    suspend fun recordTombstones(entries: List<SyncTombstoneEntity>)

    @Upsert
    suspend fun upsertMembers(members: List<MemberEntity>)

    @Transaction
    suspend fun upsertBatchWithRowsTracked(
        batch: IntakeBatchEntity,
        rows: List<IntakeRowEntity>,
        changes: List<SyncOutboxEntity>,
    ) {
        upsertBatchWithRows(batch, rows)
        changes.forEach { recordChange(it) }
    }

    @Transaction
    suspend fun upsertRowsTracked(rows: List<IntakeRowEntity>, changes: List<SyncOutboxEntity>) {
        upsertRows(rows)
        changes.forEach { recordChange(it) }
    }

    @Transaction
    suspend fun setStatusTracked(id: String, status: String, change: SyncOutboxEntity) {
        setStatus(id, status)
        recordChange(change)
    }

    /**
     * Importing writes members and closes the batch, and it now does both in one transaction.
     *
     * They were two calls through two different DAOs, so a failure between them left members
     * created from a sheet still marked REVIEWING -- importing it again would have registered
     * every one of them a second time, with fresh membership numbers.
     */
    @Transaction
    suspend fun importTracked(
        batchId: String,
        status: String,
        members: List<MemberEntity>,
        changes: List<SyncOutboxEntity>,
    ) {
        if (members.isNotEmpty()) upsertMembers(members)
        setStatus(batchId, status)
        changes.forEach { recordChange(it) }
    }

    @Transaction
    suspend fun deleteBatchTracked(id: String, nowEpochMs: Long) {
        val rowIds = rowIdsForBatch(id)
        val tombstones = buildList {
            add(SyncTombstoneEntity(SyncTables.INTAKE_BATCHES, id, nowEpochMs))
            rowIds.forEach { add(SyncTombstoneEntity(SyncTables.INTAKE_ROWS, it, nowEpochMs)) }
        }
        recordTombstones(tombstones)
        tombstones.forEach {
            recordChange(
                SyncOutboxEntity(
                    tableName = it.tableName,
                    rowId = it.rowId,
                    op = SyncOp.DELETE.name,
                    capturedAtEpochMs = nowEpochMs,
                ),
            )
        }
        deleteBatch(id)
    }

    /** Read before the delete: `intake_rows` CASCADEs, so afterwards these ids are unknowable. */
    @Query("SELECT id FROM intake_rows WHERE batch_id = :batchId")
    suspend fun rowIdsForBatch(batchId: String): List<String>
}
