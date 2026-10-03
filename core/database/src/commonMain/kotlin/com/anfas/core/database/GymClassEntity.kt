package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One recurring slot in the weekly timetable.
 *
 * [dayOfWeek] is stored as `DayOfWeek`'s ISO number (Monday = 1) rather than its name, because
 * ordering the week is the query's job and `ORDER BY name` would sort Friday first. The enum's
 * `isoDayNumber` is a stable part of its public API, so this survives a rename.
 *
 * [startMinuteOfDay] likewise: minutes since midnight sorts correctly as an integer, whereas
 * "09:00" and "10:00" only sort correctly as text by accident of zero-padding, and a stored
 * "9:00" would break it.
 *
 * **No foreign key on [instructorStaffId]**, and that is a different decision from `check_ins`
 * for a different reason. Check-ins have no key because history must survive a deleted member.
 * Here the reference is live and *should* follow a rename — but a disabled or removed coach must
 * not delete next week's classes off the timetable, which is exactly what a CASCADE would do and
 * what SET NULL would half-do while hiding that the slot needs reassigning. The join is done in
 * the repository and an unresolvable id reads as "unassigned", which is a real state anyway.
 */
@Entity(
    tableName = "scheduled_classes",
    indices = [Index(value = ["day_of_week", "start_minute_of_day"])],
)
data class GymClassEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** `ClassCategory` name. Persistence writes `.name`, so renaming an entry is a migration. */
    val category: String,
    val room: String,
    val capacity: Int,
    @ColumnInfo(name = "instructor_staff_id") val instructorStaffId: String?,
    /** `DayOfWeek.isoDayNumber` — Monday is 1. */
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_minute_of_day") val startMinuteOfDay: Int,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
)

@Dao
interface GymClassDao {

    /**
     * The whole timetable, ordered so the caller never has to sort. Both designed screens read
     * every row — a week has a few dozen classes at most — so there is no paging and no per-day
     * query to keep consistent with this one.
     */
    @Query(
        "SELECT * FROM scheduled_classes " +
            "ORDER BY day_of_week, start_minute_of_day, name COLLATE NOCASE, id",
    )
    fun observeAll(): Flow<List<GymClassEntity>>

    @Query("SELECT * FROM scheduled_classes WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GymClassEntity?

    /**
     * Other classes in the same room on the same day. Overlap is decided in Kotlin rather than
     * in SQL: the interval test belongs with the rest of the timetable arithmetic in
     * `:core:model`, and a room has a handful of classes a day.
     */
    @Query(
        "SELECT * FROM scheduled_classes " +
            "WHERE room = :room AND day_of_week = :dayOfWeek AND id != :excludeId",
    )
    suspend fun findClashesInRoom(
        room: String,
        dayOfWeek: Int,
        excludeId: String,
    ): List<GymClassEntity>

    @Upsert
    suspend fun upsert(gymClass: GymClassEntity)

    @Query("DELETE FROM scheduled_classes WHERE id = :id")
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
    suspend fun upsertTracked(gymClass: GymClassEntity, change: SyncOutboxEntity) {
        upsert(gymClass)
        recordChange(change)
    }

    @Transaction
    suspend fun deleteTracked(id: String, nowEpochMs: Long) {
        recordTombstones(
            listOf(SyncTombstoneEntity(SyncTables.SCHEDULED_CLASSES, id, nowEpochMs)),
        )
        recordChange(
            SyncOutboxEntity(
                tableName = SyncTables.SCHEDULED_CLASSES,
                rowId = id,
                op = SyncOp.DELETE.name,
                capturedAtEpochMs = nowEpochMs,
            ),
        )
        delete(id)
    }

    /**
     * A bulk `UPDATE` still has to name the rows it changed, so the ids are read first and inside
     * the transaction. Recording "scheduled_classes changed" without saying which rows would push
     * nothing, and recording it after the update would read back rows that already match the new
     * value -- correct here only by luck, and wrong for any bulk update whose predicate is the
     * column it writes.
     */
    @Transaction
    suspend fun unassignInstructorTracked(staffId: String, nowEpochMs: Long) {
        val affected = idsForInstructor(staffId)
        unassignInstructor(staffId)
        affected.forEach { id ->
            recordChange(
                SyncOutboxEntity(
                    tableName = SyncTables.SCHEDULED_CLASSES,
                    rowId = id,
                    op = SyncOp.UPSERT.name,
                    capturedAtEpochMs = nowEpochMs,
                ),
            )
        }
    }

    @Query("SELECT id FROM scheduled_classes WHERE instructor_staff_id = :staffId")
    suspend fun idsForInstructor(staffId: String): List<String>

    /**
     * Clears a coach off every slot they teach.
     *
     * Called when an account is disabled, so the timetable says "unassigned" — which is true and
     * actionable — rather than naming somebody who no longer works here.
     */
    @Query(
        "UPDATE scheduled_classes SET instructor_staff_id = NULL WHERE instructor_staff_id = :staffId",
    )
    suspend fun unassignInstructor(staffId: String)
}
