package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A queued/sent/failed WhatsApp reminder.
 *
 * Member name and phone are copied onto the row rather than joined: the queue must stay
 * readable and searchable on its own, and must still show who a message was addressed to
 * after the member's details change.
 *
 * Failure fields are flat nullable columns instead of an embedded type so that a row with no
 * failure costs nothing and Room needs no converters.
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["status", "scheduled_at_epoch_ms"]),
        Index(value = ["member_id"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    @ColumnInfo(name = "member_name") val memberName: String,
    val phone: String,
    val template: String,
    @ColumnInfo(name = "scheduled_at_epoch_ms") val scheduledAtEpochMs: Long,
    val attempts: Int,
    val status: String,
    @ColumnInfo(name = "failure_reason") val failureReason: String?,
    @ColumnInfo(name = "failure_provider_code") val failureProviderCode: Int?,
    @ColumnInfo(name = "failure_last_attempt_epoch_ms") val failureLastAttemptEpochMs: Long?,
    @ColumnInfo(name = "failure_detail") val failureDetail: String?,
)

/** Projection for the tab badges. */
data class ReminderStatusCount(val status: String, val count: Int)

@Dao
interface ReminderDao {

    /**
     * Newest first: the queue is a worklist, and today's 06:00 batch is what staff act on.
     * A blank [query] means no text filter — SQLite has no way to express "optional
     * predicate", so the blank case is folded into the WHERE clause rather than duplicating
     * the query.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status = :status
          AND (:query = '' OR member_name LIKE '%' || :query || '%' COLLATE NOCASE
                           OR phone LIKE '%' || :query || '%')
          AND (:template IS NULL OR template = :template)
        ORDER BY scheduled_at_epoch_ms DESC
        """,
    )
    fun observeByStatus(
        status: String,
        query: String = "",
        template: String? = null,
    ): Flow<List<ReminderEntity>>

    /** Drives the Queued/Sent/Failed badge counts in one subscription rather than three. */
    @Query("SELECT status, COUNT(*) AS count FROM reminders GROUP BY status")
    fun observeStatusCounts(): Flow<List<ReminderStatusCount>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: String): Flow<ReminderEntity?>

    /**
     * Which of these ids already have a row, whatever their status.
     *
     * The scheduler derives a deterministic id per term, so this is how it stays idempotent: build
     * the candidate ids, ask which already exist, insert only the rest. It deliberately ignores
     * status — a reminder already SENT or FAILED for a term must not be recreated as QUEUED, which
     * is exactly what re-running would otherwise do through `@Upsert` (it replaces every column by
     * id, resetting attempts and clearing the failure).
     */
    @Query("SELECT id FROM reminders WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    @Upsert
    suspend fun upsertAll(reminders: List<ReminderEntity>)

    /**
     * Requeues a failed message: clears the failure and bumps the attempt count in one
     * statement, so a retry can never leave a QUEUED row still carrying a failure reason.
     */
    @Query(
        """
        UPDATE reminders
        SET status = 'QUEUED',
            attempts = attempts + 1,
            failure_reason = NULL,
            failure_provider_code = NULL,
            failure_last_attempt_epoch_ms = NULL,
            failure_detail = NULL
        WHERE id IN (:ids)
        """,
    )
    suspend fun requeue(ids: List<String>): Int
}
