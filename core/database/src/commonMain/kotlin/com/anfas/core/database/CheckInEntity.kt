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
 * One entry attempt. Append-only: a check-in is a historical fact, so nothing here is ever
 * updated or deleted.
 *
 * [memberName] and [membershipNumber] are **copied, not joined**. If a member is renamed or
 * removed, the log must still say who walked in that afternoon — a join would rewrite history,
 * and there is deliberately no foreign key for the same reason: deleting a member must not
 * cascade away the record of them having been here.
 *
 * Indexed on the timestamp because every query is "today", ordered newest first.
 */
@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["at_epoch_ms"]), Index(value = ["member_id"])],
)
data class CheckInEntity(
    @PrimaryKey val id: String,
    /** Null when nobody could be identified. */
    @ColumnInfo(name = "member_id") val memberId: String?,
    @ColumnInfo(name = "member_name") val memberName: String,
    @ColumnInfo(name = "membership_number") val membershipNumber: String,
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    /** Holds [com.anfas.core.model.CheckInOutcome] by name. */
    val outcome: String,
)

@Dao
interface CheckInDao {

    /**
     * Entries within a half-open millisecond range, newest first.
     *
     * A range rather than a stored date column: "today" depends on the device's time zone, which
     * storage must not decide. The caller computes the day's bounds and asks for them.
     */
    @Query(
        """
        SELECT * FROM check_ins
        WHERE at_epoch_ms >= :fromEpochMs AND at_epoch_ms < :untilEpochMs
        ORDER BY at_epoch_ms DESC
        """,
    )
    fun observeBetween(fromEpochMs: Long, untilEpochMs: Long): Flow<List<CheckInEntity>>

    /** One member's history, for the profile. Newest first, capped by the caller. */
    @Query(
        """
        SELECT * FROM check_ins
        WHERE member_id = :memberId
        ORDER BY at_epoch_ms DESC
        LIMIT :limit
        """,
    )
    fun observeForMember(memberId: String, limit: Int): Flow<List<CheckInEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM check_ins
        WHERE member_id = :memberId
          AND outcome = 'GRANTED'
          AND at_epoch_ms >= :fromEpochMs AND at_epoch_ms < :untilEpochMs
        """,
    )
    fun observeGrantedCount(memberId: String, fromEpochMs: Long, untilEpochMs: Long): Flow<Int>

    // Insert, never upsert: an append-only log has nothing to replace, and an upsert would let a
    // duplicated id silently overwrite an earlier entry instead of failing.
    @Insert
    suspend fun insert(checkIn: CheckInEntity)

    // --- sync bookkeeping -------------------------------------------------------------------
    // Declared here, not only on SyncDao, so an outbox entry shares a @Transaction with the write
    // it describes. See SyncOutboxEntity: a change committed with no record of it never syncs,
    // and nothing afterwards can detect that it happened.

    @Insert
    suspend fun recordChange(entry: SyncOutboxEntity)

    @Upsert
    suspend fun upsertMember(member: MemberEntity)

    /**
     * The entry, the member's last-seen stamp and both outbox records, in one transaction.
     *
     * These were two unrelated DAO calls before, so a failure between them could log a check-in
     * the directory did not reflect. [member] is null for a refused attempt: a refusal is not a
     * visit, and moving `lastCheckInAt` would make the directory claim an expired member trained
     * today.
     */
    @Transaction
    suspend fun insertTracked(
        checkIn: CheckInEntity,
        member: MemberEntity?,
        changes: List<SyncOutboxEntity>,
    ) {
        insert(checkIn)
        member?.let { upsertMember(it) }
        changes.forEach { recordChange(it) }
    }
}
