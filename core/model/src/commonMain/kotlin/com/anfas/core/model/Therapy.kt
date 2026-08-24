package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * A patient's physical-therapy record — the export's `therapy-case-file`, and, per its own
 * banner, visible only to a therapist or the Owner. One member holds at most one **open** case at
 * a time; [status] is how that is enforced, and a closed case is never deleted, so the history of
 * a resolved injury stays on the record if it flares up again.
 *
 * [referredBy] is free text on purpose — "Dr. Amira Saleh" is an outside physician with no
 * account in this app, not a [MemberId] or a staff id.
 *
 * [therapistStaffId] carries no foreign key, resolved at read time the same way
 * `GymClass.instructorStaffId` is: the treating clinician should follow a rename, and an id that
 * stops resolving reads as unassigned rather than as an error.
 *
 * [contraindications] is nullable rather than an empty string standing for "none" — the export's
 * warning box only ever appears when there is something to warn about, and an empty box that says
 * nothing is worse than an absent one.
 */
data class TherapyCase(
    val id: TherapyCaseId,
    val memberId: MemberId,
    /** "Right shoulder impingement" — what is being treated, and the one field that must exist. */
    val condition: String,
    val status: CaseStatus,
    val openedOn: LocalDate,
    val closedOn: LocalDate?,
    val therapistStaffId: String?,
    val referredBy: String?,
    /** How the injury began — "Late May 2026". Free text: patients rarely recall an exact date. */
    val onset: String,
    /** How it happened, in the patient's or therapist's own words. */
    val mechanism: String,
    val contraindications: String?,
) {
    val isOpen: Boolean get() = status == CaseStatus.ACTIVE
}

enum class CaseStatus {
    ACTIVE,
    CLOSED,
}

/**
 * One logged visit against a [TherapyCase].
 *
 * [treatmentTypes] may be empty — a session can be a pure consultation with nothing performed —
 * so it is not validated as non-empty the way `condition` is on the case itself.
 *
 * [painScore] is 0–10 self-reported, matching the export's "Pain reported at 3/10 today." Null
 * means the therapist did not ask or the patient could not give one, which is a real state, not
 * a missing one — [TherapyProgress] treats it as absent rather than as zero.
 */
data class TherapySession(
    val id: TherapySessionId,
    val caseId: TherapyCaseId,
    val therapistStaffId: String?,
    val at: Instant,
    val durationMinutes: Int,
    val treatmentTypes: Set<TreatmentType>,
    val notes: String,
    val painScore: Int?,
) {
    companion object {
        const val MIN_DURATION_MINUTES: Int = 5
        const val MAX_DURATION_MINUTES: Int = 4 * 60
        val PAIN_SCORE_RANGE: IntRange = 0..10
    }
}

/**
 * The export's own vocabulary from `therapy-case-file`'s session cards, and no others — a
 * physiotherapy practice offers dozens of modalities, and guessing at ones the export never
 * showed would be inventing scope nobody asked for.
 */
enum class TreatmentType {
    MANUAL_THERAPY,
    EXERCISE,
    DRY_NEEDLING,
    ULTRASOUND,
}

/**
 * The export's "Pain Score 7 → 3" trend line, computed from real recorded scores rather than
 * shown as a fabricated number.
 *
 * Deliberately **not** attempted: the export's "Shoulder Flexion 115° → 158°" range-of-motion
 * figure. That is a condition-specific measurement — a shoulder case tracks degrees of flexion, a
 * knee case would track something else entirely — and modelling it honestly needs a general
 * named-metric system, which is a real feature to design, not a field to guess at here. Pain
 * score is the one measurement every case can report the same way, so it is the one built.
 */
object TherapyProgress {

    /**
     * Null when there are fewer than two sessions with a recorded pain score — a single data
     * point is not a trend, and showing "7 → 7" from one session double-counts it.
     */
    fun painScoreTrend(sessions: List<TherapySession>): PainScoreTrend? {
        val scored = sessions.sortedBy {
            it.at
        }.mapNotNull { it.painScore?.let { s -> it.at to s } }
        if (scored.size < 2) return null
        return PainScoreTrend(
            scores = scored.map { it.second },
            first = scored.first().second,
            latest = scored.last().second,
        )
    }
}

/** [scores] is oldest-first, for a sparkline; [first] and [latest] are its two ends. */
data class PainScoreTrend(val scores: List<Int>, val first: Int, val latest: Int) {
    /** True when pain has gone down — the direction that reads as progress, not just change. */
    val isImproving: Boolean get() = latest < first
}
