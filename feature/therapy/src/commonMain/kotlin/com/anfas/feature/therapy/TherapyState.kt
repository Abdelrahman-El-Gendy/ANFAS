package com.anfas.feature.therapy

import com.anfas.core.data.CaseProblem
import com.anfas.core.data.SessionProblem
import com.anfas.core.data.TherapyCaseDetail
import com.anfas.core.model.CaseStatus
import com.anfas.core.model.Member
import com.anfas.core.model.PainScoreTrend
import com.anfas.core.model.TherapyProgress
import com.anfas.core.model.TreatmentType

/**
 * One member's therapy screen. Reached from their profile, exactly as Renewal is — there is no
 * roster to browse, so the screen loads the member alongside whatever case exists for them.
 *
 * [TherapyCaseDetail] is null when the member has no case yet, which is not an error: the screen
 * renders that as an "Open case" invitation rather than as a failure.
 */
sealed interface TherapyContent {
    data object Loading : TherapyContent

    data class Loaded(val member: Member, val detail: TherapyCaseDetail?) : TherapyContent

    /** The member row is gone — deleted on another device since this screen was opened. */
    data object MemberMissing : TherapyContent

    data class Failed(val message: String) : TherapyContent
}

data class TherapyState(
    val content: TherapyContent = TherapyContent.Loading,
    val caseForm: CaseForm? = null,
    val sessionForm: SessionForm? = null,
    val closeConfirmVisible: Boolean = false,
    val notice: TherapyNotice? = null,
    /**
     * Every enabled staff member, for the therapist picker — independent of whether a case
     * exists yet, unlike `TherapyCaseDetail.staffNames`, which only resolves names already on
     * the case. Sorted by name so the chip row reads alphabetically rather than by id.
     */
    val staffOptions: List<Pair<String, String>> = emptyList(),
) {
    val detail: TherapyCaseDetail? get() = (content as? TherapyContent.Loaded)?.detail

    val progress: PainScoreTrend?
        get() = detail?.let { TherapyProgress.painScoreTrend(it.sessions) }

    /** Only one open case per member, so a case that is CLOSED still shows "Open case". */
    val canOpenNewCase: Boolean
        get() = content is TherapyContent.Loaded && detail?.case?.status != CaseStatus.ACTIVE

    val canLogSession: Boolean get() = detail?.case?.status == CaseStatus.ACTIVE
    val canCloseCase: Boolean get() = detail?.case?.status == CaseStatus.ACTIVE
}

/**
 * The open/edit-case form. [editing] distinguishes the two the same way `ClassForm.editing`
 * does: null means a new case, non-null means updating the one already on file.
 */
data class CaseForm(
    val editing: Boolean = false,
    val condition: String = "",
    val therapistStaffId: String? = null,
    val referredBy: String = "",
    val onset: String = "",
    val mechanism: String = "",
    val contraindications: String = "",
    val isSubmitting: Boolean = false,
    val problems: Set<CaseProblem> = emptySet(),
) {
    val canSubmit: Boolean get() = !isSubmitting && condition.isNotBlank()
}

/**
 * The log-session form. [dayOffset] is 0–7 rather than a date picker: sessions are logged same
 * week almost always, and the export's own cards never show one older than a few weeks. A bounded
 * chip set, the same reasoning as `ClassForm`'s START_TIMES.
 */
data class SessionForm(
    val therapistStaffId: String? = null,
    val dayOffset: Int = 0,
    val durationMinutes: Int = 45,
    val treatmentTypes: Set<TreatmentType> = emptySet(),
    val notes: String = "",
    /** Text, not `Int?` directly, so the field can be cleared and retyped like any other. */
    val painScoreText: String = "",
    val isSubmitting: Boolean = false,
    val problems: Set<SessionProblem> = emptySet(),
) {
    val canSubmit: Boolean get() = !isSubmitting
}

sealed interface TherapyNotice {
    data class CaseOpened(val condition: String) : TherapyNotice
    data object CaseClosed : TherapyNotice
    data object SessionLogged : TherapyNotice
    data object AlreadyOpen : TherapyNotice
    data class Failed(val message: String) : TherapyNotice
}
