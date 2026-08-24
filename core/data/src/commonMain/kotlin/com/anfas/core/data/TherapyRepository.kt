package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.CaseStatus
import com.anfas.core.model.MemberId
import com.anfas.core.model.TherapyCase
import com.anfas.core.model.TherapyCaseId
import com.anfas.core.model.TherapySession
import com.anfas.core.model.TreatmentType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One member's physical-therapy record.
 *
 * Scoped to a member rather than exposing a caseload across every member, on purpose: the export
 * designed exactly one screen for this feature — a single patient's case file — and no roster.
 * The entry point is the member's own profile, the same way Renewal is; there is no "Recovery"
 * list screen for a therapist to browse their whole caseload, because building one would be
 * inventing a screen the export never drew. See CLAUDE.md.
 */
interface TherapyRepository {

    /**
     * The most recently opened case for [memberId], active or closed, or null when none exists
     * yet. Null is not a failure state — it is what "Open case" is for.
     */
    fun observeLatestCase(memberId: MemberId): Flow<AppResult<TherapyCaseDetail?>>

    /**
     * Refused with [SaveCaseOutcome.Invalid] when [condition] is blank. Refused entirely — not
     * even attempted — when [memberId] already holds an open case: the export's record is
     * singular per patient, and a second simultaneous case would be ambiguous about which one
     * "the" case file means.
     */
    suspend fun openCase(
        memberId: MemberId,
        condition: String,
        therapistStaffId: String?,
        referredBy: String?,
        onset: String,
        mechanism: String,
        contraindications: String?,
        openedOn: LocalDate,
    ): AppResult<SaveCaseOutcome>

    /** Edits the case's own fields. Never touches [CaseStatus] — see [closeCase]. */
    suspend fun updateCase(
        caseId: TherapyCaseId,
        condition: String,
        therapistStaffId: String?,
        referredBy: String?,
        onset: String,
        mechanism: String,
        contraindications: String?,
    ): AppResult<SaveCaseOutcome>

    /**
     * Sets [CaseStatus.CLOSED] and stamps [TherapyCase.closedOn]. Never deletes: a resolved
     * injury's history stays on the record if it flares up again.
     */
    suspend fun closeCase(caseId: TherapyCaseId, closedOn: LocalDate): AppResult<Unit>

    suspend fun logSession(
        caseId: TherapyCaseId,
        therapistStaffId: String?,
        at: Instant,
        durationMinutes: Int,
        treatmentTypes: Set<TreatmentType>,
        notes: String,
        painScore: Int?,
    ): AppResult<SaveSessionOutcome>
}

/**
 * A case with its sessions and the staff names it refers to, resolved once here rather than by
 * every screen that renders a therapist's name — the same shape as `Timetable`.
 */
data class TherapyCaseDetail(
    val case: TherapyCase,
    /** Newest first, matching the export's session list. */
    val sessions: List<TherapySession>,
    val staffNames: Map<String, String>,
) {
    fun therapistName(therapistStaffId: String?): String? = therapistStaffId?.let { staffNames[it] }
}

sealed interface SaveCaseOutcome {
    data class Saved(val caseId: TherapyCaseId) : SaveCaseOutcome
    data class Invalid(val problems: Set<CaseProblem>) : SaveCaseOutcome

    /** Refused outright: [TherapyRepository.openCase] found an already-open case. */
    data object AlreadyOpen : SaveCaseOutcome
}

enum class CaseProblem { CONDITION_BLANK }

sealed interface SaveSessionOutcome {
    data object Saved : SaveSessionOutcome
    data class Invalid(val problems: Set<SessionProblem>) : SaveSessionOutcome
}

enum class SessionProblem { DURATION_OUT_OF_RANGE, PAIN_SCORE_OUT_OF_RANGE }
