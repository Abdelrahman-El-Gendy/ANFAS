package com.anfas.core.database

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
 * Storage shape for a member. Kept flat and primitive on purpose: no domain enums or
 * value classes cross into Room, so a rename in the domain never forces a migration.
 * [status] holds [com.anfas.core.model.MembershipStatus] by name.
 *
 * [lastCheckInAtEpochMs] is nullable — a member who has never checked in has no timestamp,
 * which is different from checking in at epoch zero.
 */
@Entity(
    tableName = "members",
    indices = [
        Index(value = ["membership_number"], unique = true),
        // Not unique: a family can legitimately share a landline, and OCR intake reports
        // duplicates for a human to resolve rather than having the database reject them.
        Index(value = ["phone_normalised"]),
    ],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    @androidx.room3.ColumnInfo(name = "membership_number") val membershipNumber: String,
    val phone: String?,
    /**
     * Digits-only form of [phone], written by the mapper. Stored rather than computed so
     * duplicate lookups are an indexed equality test instead of a full scan with string
     * munging in SQL.
     */
    @androidx.room3.ColumnInfo(name = "phone_normalised") val phoneNormalised: String?,
    val status: String,
    val lastCheckInAtEpochMs: Long?,
    val avatarUrl: String?,
    /**
     * WhatsApp consent.
     *
     * **`defaultValue` is required, and KSP enforces it** — this is the first column ever added to
     * an existing table in this schema, so it is the first time it has mattered. Removing it fails
     * the build outright: *"New NOT NULL column 'whatsapp_opt_in' added with no default value
     * specified"*. Room has nothing to write into the rows already on disk. Note it is a SQL
     * literal, so the string `"0"` and not `false`.
     *
     * Which value is *not* enforced by anything but `MigrationFromV4Test`, and that is what the
     * assertion there is for: `"1"` would compile happily and silently opt in every member a gym
     * already has. Consent is asked for, never inferred.
     */
    @androidx.room3.ColumnInfo(name = "whatsapp_opt_in", defaultValue = "0")
    val whatsappOptIn: Boolean,
    /**
     * Holds [com.anfas.core.model.TemplateLanguage] by name. Nullable — "not asked" is a real
     * state, distinct from either language — so no default is needed.
     */
    @androidx.room3.ColumnInfo(name = "preferred_language") val preferredLanguage: String?,
)

@Dao
interface MemberDao {
    /**
     * Ordered by name because the directory is browsed alphabetically, not by insertion.
     * Returns a Flow so the list screen updates itself after a check-in or a renewal.
     */
    @Query("SELECT * FROM members ORDER BY fullName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MemberEntity>>

    /**
     * Every phone number already on the books, for OCR intake's duplicate check. Returns just
     * the column rather than whole rows — a sheet is validated against the entire membership,
     * and loading every member to read one field each would not scale.
     */
    @Query("SELECT phone_normalised FROM members WHERE phone_normalised IS NOT NULL")
    fun observeNormalisedPhones(): Flow<List<String>>

    /**
     * Matches name or membership number. `search-no-results` renders the raw query back to
     * the user, so the caller keeps the term; this only reports matches.
     */
    @Query(
        """
        SELECT * FROM members
        WHERE fullName LIKE '%' || :query || '%' COLLATE NOCASE
           OR membership_number LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY fullName COLLATE NOCASE ASC
        """,
    )
    fun observeMatching(query: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE id = :id")
    fun observeById(id: String): Flow<MemberEntity?>

    @Upsert
    suspend fun upsertAll(members: List<MemberEntity>)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteById(id: String)

    // --- sync bookkeeping -------------------------------------------------------------------
    //
    // The outbox insert is declared here rather than only on SyncDao so it can share a
    // @Transaction with the write it describes. A Room DAO may insert any entity, and the point
    // is that the two land together or neither does: a change committed with no record of it is
    // a change that never syncs, and nothing later can detect that it happened.

    @Insert
    suspend fun recordChange(entry: SyncOutboxEntity)

    @Insert
    suspend fun recordTombstones(entries: List<SyncTombstoneEntity>)

    @Transaction
    suspend fun upsertAllTracked(members: List<MemberEntity>, changes: List<SyncOutboxEntity>) {
        upsertAll(members)
        changes.forEach { recordChange(it) }
    }

    /**
     * Deletes a member, recording a tombstone for them **and for everything SQLite is about to
     * cascade away beneath them**.
     *
     * The child ids are read inside the transaction and before the delete, because after it they
     * are unknowable: `therapy_cases` CASCADEs from `members` and `therapy_sessions` CASCADEs
     * from `therapy_cases`, so one statement can remove rows from three tables. Without this the
     * other device would delete the member and keep the clinical narrative, attached to nothing.
     *
     * Reading two levels rather than reimplementing the cascade is deliberate: a hand-written
     * cascade would have to stay in step with the schema on the device *and* on the server, and
     * that is the highest-risk code this feature could contain. These two queries only have to
     * agree with the foreign keys, which the Room schema pins.
     */
    @Transaction
    suspend fun deleteByIdTracked(id: String, nowEpochMs: Long) {
        val caseIds = therapyCaseIdsForMember(id)
        val sessionIds = if (caseIds.isEmpty()) emptyList() else therapySessionIdsForCases(caseIds)

        val tombstones = buildList {
            add(SyncTombstoneEntity(SyncTables.MEMBERS, id, nowEpochMs))
            caseIds.forEach { add(SyncTombstoneEntity(SyncTables.THERAPY_CASES, it, nowEpochMs)) }
            sessionIds.forEach {
                add(SyncTombstoneEntity(SyncTables.THERAPY_SESSIONS, it, nowEpochMs))
            }
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
        deleteById(id)
    }

    /** Cascade lookups. Declared here because they are read as part of deleting a member. */
    @Query("SELECT id FROM therapy_cases WHERE member_id = :memberId")
    suspend fun therapyCaseIdsForMember(memberId: String): List<String>

    @Query("SELECT id FROM therapy_sessions WHERE case_id IN (:caseIds)")
    suspend fun therapySessionIdsForCases(caseIds: List<String>): List<String>
}
