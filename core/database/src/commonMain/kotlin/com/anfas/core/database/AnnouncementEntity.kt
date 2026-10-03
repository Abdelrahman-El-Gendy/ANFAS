package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A staff bulletin. No foreign key on [createdByStaffId] — resolved against `staff` at read
 * time, the same as `GymClassEntity.instructorStaffId` — and none on any member, because this
 * table has no per-member row at all: [audience] names a *segment*, not a recipient list, and
 * [recipientCountAtPublish] is the one number frozen from that segment at publish time.
 */
@Entity(tableName = "announcements")
data class AnnouncementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    /** `AnnouncementAudience` name. */
    val audience: String,
    @ColumnInfo(name = "event_date_epoch_day") val eventDateEpochDay: Long?,
    @ColumnInfo(name = "event_minute_of_day") val eventMinuteOfDay: Int?,
    /** `AnnouncementStatus` name. */
    val status: String,
    @ColumnInfo(name = "created_by_staff_id") val createdByStaffId: String?,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "published_at_epoch_ms") val publishedAtEpochMs: Long?,
    @ColumnInfo(name = "recipient_count_at_publish") val recipientCountAtPublish: Int?,
)

@Dao
interface AnnouncementDao {

    /** Newest first, drafts and published mixed — the most recently touched thing first. */
    @Query("SELECT * FROM announcements ORDER BY created_at_epoch_ms DESC")
    fun observeAll(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AnnouncementEntity?

    @Upsert
    suspend fun upsert(announcement: AnnouncementEntity)

    @Query("DELETE FROM announcements WHERE id = :id")
    suspend fun delete(id: String)

    // --- sync bookkeeping -------------------------------------------------------------------
    // Declared here, not only on SyncDao, so an outbox entry shares a @Transaction with the write
    // it describes. See SyncOutboxEntity: a change committed with no record of it never syncs,
    // and nothing afterwards can detect that it happened.

    @Insert
    suspend fun recordChange(entry: SyncOutboxEntity)

    @Insert
    suspend fun recordTombstones(entries: List<SyncTombstoneEntity>)

    @Transaction
    suspend fun upsertTracked(announcement: AnnouncementEntity, change: SyncOutboxEntity) {
        upsert(announcement)
        recordChange(change)
    }

    @Transaction
    suspend fun deleteTracked(id: String, nowEpochMs: Long) {
        recordTombstones(listOf(SyncTombstoneEntity(SyncTables.ANNOUNCEMENTS, id, nowEpochMs)))
        recordChange(
            SyncOutboxEntity(
                tableName = SyncTables.ANNOUNCEMENTS,
                rowId = id,
                op = SyncOp.DELETE.name,
                capturedAtEpochMs = nowEpochMs,
            ),
        )
        delete(id)
    }
}
