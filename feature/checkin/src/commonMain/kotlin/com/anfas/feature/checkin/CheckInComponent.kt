package com.anfas.feature.checkin

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.CheckInRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.model.CheckInSummary
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The reception desk: find a member, let them in, and see today's entries.
 *
 * The component never decides the outcome — [CheckInRepository.recordAttempt] does, from the
 * member's status and term. A screen that could pass "granted" would eventually let an expired
 * member in by sending the wrong flag, and the whole point of the log is that it reflects the
 * rule rather than what someone tapped.
 *
 * Search is debounced through `flatMapLatest` like the members directory: typing cancels the
 * previous query rather than racing it, so results cannot arrive out of order.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CheckInComponent(
    componentContext: ComponentContext,
    private val members: MemberRepository,
    private val checkIns: CheckInRepository,
    dispatchers: AppDispatchers,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val onMemberClicked: (MemberId) -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("CheckIn"))

    private val ui = MutableStateFlow(UiState())

    // Resolved once per component rather than per emission: a desk session does not span
    // midnight, and re-reading it on every keystroke would rebuild the log query each time.
    private val today = Clock.System.todayIn(zone)

    val state: StateFlow<CheckInState> = combine(
        ui,
        ui.map { it.query }
            .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
            .distinctUntilChanged()
            .flatMapLatest { term ->
                // Nothing typed means no query at all: listing every member at a desk that is
                // about to type a name is noise, and the design shows a prompt instead.
                if (term.isBlank()) {
                    MutableStateFlow<Pair<String, AppResult<List<Member>>>?>(
                        null,
                    )
                } else {
                    members.observeMembers(term).map { term to it }
                }
            },
        checkIns.observeDay(today),
        checkIns.observeDaySummary(today),
    ) { local, searched, dayResult, summaryResult ->
        val searchError = (searched?.second as? AppResult.Failure)?.error?.message
        val logError = (dayResult as? AppResult.Failure)?.error?.message
        val summaryError = (summaryResult as? AppResult.Failure)?.error?.message

        CheckInState(
            query = local.query,
            search = searched.toSearch(),
            summary = (summaryResult as? AppResult.Success)?.value ?: CheckInSummary(),
            log = (dayResult as? AppResult.Success)?.value.orEmpty(),
            recordingId = local.recordingId,
            notice = local.notice,
            // The log failing matters more than the search failing: the search can be retyped,
            // whereas a log that silently shows nothing looks like an empty gym.
            error = logError ?: summaryError ?: searchError,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = CheckInState(),
    )

    fun onQueryChanged(value: String) = ui.update { it.copy(query = value, notice = null) }

    fun onClearSearch() = ui.update { it.copy(query = "") }

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    fun onMemberSelected(id: MemberId) = onMemberClicked(id)

    fun onCheckIn(id: MemberId) {
        if (ui.value.recordingId != null) return
        ui.update { it.copy(recordingId = id.value, notice = null) }

        scope.launch {
            val notice = when (val result = checkIns.recordAttempt(id, today)) {
                is AppResult.Failure -> CheckInNotice.Failed(result.error.message)
                is AppResult.Success -> CheckInNotice.Recorded(result.value)
            }
            // The search is cleared on success: the next person in the queue is a new lookup, and
            // leaving the previous name in the box invites checking them in twice.
            ui.update {
                it.copy(
                    recordingId = null,
                    notice = notice,
                    query = if (notice is CheckInNotice.Recorded) "" else it.query,
                )
            }
        }
    }

    private data class UiState(
        val query: String = "",
        val recordingId: String? = null,
        val notice: CheckInNotice? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

private fun Pair<String, AppResult<List<Member>>>?.toSearch(): CheckInSearch {
    if (this == null) return CheckInSearch.Idle
    val (term, result) = this
    val found = (result as? AppResult.Success)?.value.orEmpty()
    return if (found.isEmpty()) CheckInSearch.NoMatches(term) else CheckInSearch.Results(found)
}
