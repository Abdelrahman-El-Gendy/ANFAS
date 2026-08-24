package com.anfas.feature.therapy

import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CaseProblem
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.SaveCaseOutcome
import com.anfas.core.data.SaveSessionOutcome
import com.anfas.core.data.SessionProblem
import com.anfas.core.data.TherapyCaseDetail
import com.anfas.core.data.TherapyRepository
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.TherapyCaseId
import com.anfas.core.model.TreatmentType
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * One member's physical-therapy record.
 *
 * Loads the member and the case side by side, the same reasoning as `MemberProfileComponent`
 * combining a member with its subscription term: the header needs the member's name whether or
 * not a case exists yet, so neither is allowed to gate the other.
 */
class TherapyComponent(
    componentContext: ComponentContext,
    private val memberId: MemberId,
    private val members: MemberRepository,
    private val therapy: TherapyRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val onBackClicked: () -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("Therapy"))

    private val ui = MutableStateFlow(UiState())

    /**
     * Read synchronously by [onOpenCaseForm] and [onLogSession] to default the therapist picker
     * to whoever is signed in — a small convenience, not a permission check: the router already
     * enforces `VIEW_THERAPY` before this screen composes at all.
     */
    private val currentStaffId: StateFlow<String?> = auth.observeSession()
        .map { it?.userId }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    val state: StateFlow<TherapyState> = combine(
        ui,
        members.observeMember(memberId),
        therapy.observeLatestCase(memberId),
        auth.observeStaff(),
    ) { local, memberResult, caseResult, staffResult ->
        TherapyState(
            content = contentOf(memberResult, caseResult),
            caseForm = local.caseForm,
            sessionForm = local.sessionForm,
            closeConfirmVisible = local.closeConfirmVisible,
            notice = local.notice,
            staffOptions = (staffResult as? AppResult.Success)?.value
                .orEmpty()
                .filter { it.isEnabled }
                .sortedBy { it.displayName }
                .map { it.id to it.displayName },
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TherapyState(),
    )

    fun onBack() = onBackClicked()

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    /** Opens the form for a new case, defaulting the therapist to whoever is signed in. */
    fun onOpenCaseForm() = ui.update {
        it.copy(
            caseForm = CaseForm(editing = false, therapistStaffId = currentStaffId.value),
            notice = null,
        )
    }

    fun onEditCaseForm() {
        val detail = state.value.detail ?: return
        val case = detail.case
        ui.update {
            it.copy(
                caseForm = CaseForm(
                    editing = true,
                    condition = case.condition,
                    therapistStaffId = case.therapistStaffId,
                    referredBy = case.referredBy.orEmpty(),
                    onset = case.onset,
                    mechanism = case.mechanism,
                    contraindications = case.contraindications.orEmpty(),
                ),
                notice = null,
            )
        }
    }

    fun onCaseFormDismissed() = ui.update { it.copy(caseForm = null) }

    fun onCaseFormChanged(transform: (CaseForm) -> CaseForm) = ui.update { local ->
        local.copy(caseForm = local.caseForm?.let { transform(it).copy(problems = emptySet()) })
    }

    fun onSubmitCaseForm() {
        val form = state.value.caseForm ?: return
        if (!form.canSubmit) return
        ui.update { it.copy(caseForm = form.copy(isSubmitting = true)) }

        scope.launch {
            val today = clock.now().toDateIn(zone)
            val result = if (form.editing) {
                val caseId = state.value.detail?.case?.id ?: return@launch
                therapy.updateCase(
                    caseId = caseId,
                    condition = form.condition,
                    therapistStaffId = form.therapistStaffId,
                    referredBy = form.referredBy,
                    onset = form.onset,
                    mechanism = form.mechanism,
                    contraindications = form.contraindications,
                )
            } else {
                therapy.openCase(
                    memberId = memberId,
                    condition = form.condition,
                    therapistStaffId = form.therapistStaffId,
                    referredBy = form.referredBy,
                    onset = form.onset,
                    mechanism = form.mechanism,
                    contraindications = form.contraindications,
                    openedOn = today,
                )
            }

            when (result) {
                is AppResult.Failure -> ui.update {
                    it.copy(
                        caseForm = it.caseForm?.copy(isSubmitting = false),
                        notice = TherapyNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    is SaveCaseOutcome.Invalid -> ui.update {
                        it.copy(
                            caseForm = it.caseForm?.copy(
                                isSubmitting = false,
                                problems = outcome.problems,
                            ),
                        )
                    }

                    SaveCaseOutcome.AlreadyOpen -> ui.update {
                        it.copy(
                            caseForm = it.caseForm?.copy(isSubmitting = false),
                            notice = TherapyNotice.AlreadyOpen,
                        )
                    }

                    is SaveCaseOutcome.Saved -> ui.update {
                        it.copy(
                            caseForm = null,
                            notice = TherapyNotice.CaseOpened(form.condition.trim()),
                        )
                    }
                }
            }
        }
    }

    fun onCloseCaseRequested() = ui.update { it.copy(closeConfirmVisible = true) }

    fun onCloseCaseDismissed() = ui.update { it.copy(closeConfirmVisible = false) }

    fun onCloseCaseConfirmed() {
        val caseId = state.value.detail?.case?.id ?: return
        ui.update { it.copy(closeConfirmVisible = false) }
        scope.launch {
            val today = clock.now().toDateIn(zone)
            when (val result = therapy.closeCase(caseId, today)) {
                is AppResult.Failure ->
                    ui.update { it.copy(notice = TherapyNotice.Failed(result.error.message)) }

                is AppResult.Success ->
                    ui.update { it.copy(notice = TherapyNotice.CaseClosed) }
            }
        }
    }

    fun onLogSession() = ui.update {
        it.copy(
            sessionForm = SessionForm(therapistStaffId = currentStaffId.value),
            notice = null,
        )
    }

    fun onSessionFormDismissed() = ui.update { it.copy(sessionForm = null) }

    fun onSessionFormChanged(transform: (SessionForm) -> SessionForm) = ui.update { local ->
        local.copy(
            sessionForm = local.sessionForm?.let { transform(it).copy(problems = emptySet()) },
        )
    }

    fun onSubmitSessionForm() {
        val form = state.value.sessionForm ?: return
        val caseId = state.value.detail?.case?.id ?: return
        if (!form.canSubmit) return
        ui.update { it.copy(sessionForm = form.copy(isSubmitting = true)) }

        scope.launch {
            val at = clock.now() - form.dayOffset.days
            val result = therapy.logSession(
                caseId = caseId,
                therapistStaffId = form.therapistStaffId,
                at = at,
                durationMinutes = form.durationMinutes,
                treatmentTypes = form.treatmentTypes,
                notes = form.notes,
                // Blank means "not asked" -- a real state, not a missing one. The screen filters
                // this field to digits only on input (the same `digitsOnly()` guard the intake
                // and classes forms use), so a non-blank value always parses; only its *range* is
                // a validation concern, and the repository already checks that.
                painScore = form.painScoreText.trim().toIntOrNull(),
            )

            when (result) {
                is AppResult.Failure -> ui.update {
                    it.copy(
                        sessionForm = it.sessionForm?.copy(isSubmitting = false),
                        notice = TherapyNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    is SaveSessionOutcome.Invalid -> ui.update {
                        it.copy(
                            sessionForm = it.sessionForm?.copy(
                                isSubmitting = false,
                                problems = outcome.problems,
                            ),
                        )
                    }

                    SaveSessionOutcome.Saved -> ui.update {
                        it.copy(sessionForm = null, notice = TherapyNotice.SessionLogged)
                    }
                }
            }
        }
    }

    private fun contentOf(
        memberResult: AppResult<Member?>,
        caseResult: AppResult<TherapyCaseDetail?>,
    ): TherapyContent {
        val member = when (memberResult) {
            is AppResult.Failure -> return TherapyContent.Failed(memberResult.error.message)
            is AppResult.Success -> memberResult.value ?: return TherapyContent.MemberMissing
        }
        val detail = (caseResult as? AppResult.Success)?.value
        return TherapyContent.Loaded(member = member, detail = detail)
    }

    private data class UiState(
        val caseForm: CaseForm? = null,
        val sessionForm: SessionForm? = null,
        val closeConfirmVisible: Boolean = false,
        val notice: TherapyNotice? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun kotlin.time.Instant.toDateIn(zone: TimeZone) = this.toLocalDateTime(zone).date
