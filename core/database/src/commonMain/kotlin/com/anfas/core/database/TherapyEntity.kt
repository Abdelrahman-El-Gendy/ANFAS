package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A patient's physical-therapy record. CASCADEs from `members` — unlike `check_ins`, which
 * deliberately has no foreign key so a member's visit history survives their deletion, a therapy
 * case is clinical narrative that only makes sense attached to an existing member. Deleting the
 * member should take the case with it, not leave an orphaned record nothing can resolve.
 *
 * [therapistStaffId] carries no foreign key: it is resolved against `staff` at read time, the
 * same reasoning as `scheduled_classes.instructor_staff_id`.
 */
@Entity(
    tableName = "therapy_cases",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["member_id"])],
)
data class TherapyCaseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "member_id") val memberId: String,
    val condition: String,
    /** `CaseStatus` name. */
    val status: String,
    @ColumnInfo(name = "opened_on_epoch_day") val openedOnEpochDay: Long,
    @ColumnInfo(name = "closed_on_epoch_day") val closedOnEpochDay: Long?,
    @ColumnInfo(name = "therapist_staff_id") val therapistStaffId: String?,
    @ColumnInfo(name = "referred_by") val referredBy: String?,
    val onset: String,
    val mechanism: String,
    val contraindications: String?,
)

/**
 * One logged visit. CASCADEs from its case for the same reason `intake_rows` CASCADEs from
 * `intake_batches`: a session has no meaning once the case it belongs to is gone.
 */
@Entity(
    tableName = "therapy_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TherapyCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["case_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["case_id", "at_epoch_ms"])],
)
data class TherapySessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "therapist_staff_id") val therapistStaffId: String?,
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    /** Comma-separated `TreatmentType` names, the same shape as `StaffEntity.roles`. */
    @ColumnInfo(name = "treatment_types") val treatmentTypes: String,
    val notes: String,
    @ColumnInfo(name = "pain_score") val painScore: Int?,
)

@Dao
interface TherapyCaseDao {

    /**
     * The most recently opened case for a member, active or closed. A member's profile shows
     * this one: an active case is the current record, and a closed one is still worth reading
     * even though "Open case" is offered instead of "Log session" for it.
     */
    @Query(
        "SELECT * FROM therapy_cases WHERE member_id = :memberId " +
            "ORDER BY opened_on_epoch_day DESC LIMIT 1",
    )
    fun observeLatestForMember(memberId: String): Flow<TherapyCaseEntity?>

    /** One-shot form of [observeLatestForMember], for the open-case-already-exists check. */
    @Query(
        "SELECT * FROM therapy_cases WHERE member_id = :memberId " +
            "ORDER BY opened_on_epoch_day DESC LIMIT 1",
    )
    suspend fun findLatestForMember(memberId: String): TherapyCaseEntity?

    @Query("SELECT * FROM therapy_cases WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TherapyCaseEntity?

    @Upsert
    suspend fun upsertCase(case: TherapyCaseEntity)

    @Query(
        "SELECT * FROM therapy_sessions WHERE case_id = :caseId ORDER BY at_epoch_ms DESC",
    )
    fun observeSessions(caseId: String): Flow<List<TherapySessionEntity>>

    @Upsert
    suspend fun upsertSession(session: TherapySessionEntity)
}
