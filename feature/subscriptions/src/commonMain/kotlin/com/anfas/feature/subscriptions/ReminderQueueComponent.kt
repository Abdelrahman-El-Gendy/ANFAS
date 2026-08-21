package com.anfas.feature.subscriptions

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.ReminderCounts
import com.anfas.core.data.ReminderRepository
import com.anfas.core.model.MemberId
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
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

/**
 * The WhatsApp reminder queue: three status tabs, text and template filters, row and bulk
 * retry, and the failure-detail modal.
 *
 * The modal is state on this component rather than a navigation route. It is a detail overlay
 * on top of the list it belongs to — routing to it would put a full screen transition in the
 * back stack for something the design draws as a dialog over the table.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ReminderQueueComponent(
    componentContext: ComponentContext,
    private val repository: ReminderRepository,
    dispatchers: AppDispatchers,
    private val onOpenMemberClicked: (MemberId) -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("ReminderQueue"))

    private val ui = MutableStateFlow(UiSelections())

    val state: StateFlow<ReminderQueueState> = combine(
        ui,
        repository.observeCounts(),
        ui.debounce { if (it.query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .map { QueueQuery(it.status, it.query, it.template) }
            .distinctUntilChanged()
            .flatMapLatest { q ->
                repository.observeQueue(q.status, q.query, q.template).map { q to it }
            },
    ) { selections, countsResult, (queriedWith, queueResult) ->
        ReminderQueueState(
            selectedStatus = selections.status,
            counts = (countsResult as? AppResult.Success)?.value ?: ReminderCounts(),
            query = selections.query,
            templateFilter = selections.template,
            content = queueResult.toContent(queriedWith),
            // Drop any selection that is no longer on screen, so a bulk action can never act
            // on a row the user cannot see.
            selectedIds = selections.selectedIds intersect queueResult.idsOrEmpty(),
            openedFailure = queueResult.find(selections.openedId),
            notice = selections.notice,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ReminderQueueState(),
    )

    fun onStatusSelected(status: ReminderStatus) = ui.update {
        // Selection and the open modal belong to the tab they were made in.
        it.copy(status = status, selectedIds = emptySet(), openedId = null, notice = null)
    }

    fun onQueryChanged(value: String) = ui.update { it.copy(query = value, notice = null) }

    fun onTemplateFilterChanged(template: ReminderTemplate?) =
        ui.update { it.copy(template = template, notice = null) }

    fun onToggleSelected(id: ReminderId) = ui.update {
        it.copy(
            selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id,
            notice = null,
        )
    }

    /** Select-all applies to what is currently visible, not the whole tab. */
    fun onToggleSelectAll() {
        val visible = state.value.visibleReminders.map { it.id }.toSet()
        ui.update {
            it.copy(
                selectedIds = if (it.selectedIds.containsAll(visible)) emptySet() else visible,
                notice = null,
            )
        }
    }

    fun onClearSelection() = ui.update { it.copy(selectedIds = emptySet(), notice = null) }

    fun onRetrySelected() = retry(state.value.selectedIds.toList())

    fun onRetry(id: ReminderId) = retry(listOf(id))

    fun onOpenFailure(id: ReminderId) = ui.update { it.copy(openedId = id, notice = null) }

    fun onDismissFailure() = ui.update { it.copy(openedId = null) }

    fun onOpenMember(id: MemberId) = onOpenMemberClicked(id)

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    private fun retry(ids: List<ReminderId>) {
        if (ids.isEmpty()) return
        scope.launch {
            val requested = ids.size
            when (val result = repository.retry(ids)) {
                is AppResult.Failure -> ui.update {
                    it.copy(notice = QueueNotice.Failed(result.error.message))
                }

                is AppResult.Success -> ui.update {
                    it.copy(
                        selectedIds = emptySet(),
                        openedId = null,
                        // Typed, not prose: a Decompose component cannot read Compose state, so
                        // formatting here would hardcode English. The screen renders it.
                        notice = if (result.value == 0) {
                            QueueNotice.NothingRetryable
                        } else {
                            QueueNotice.Requeued(requeued = result.value, requested = requested)
                        },
                    )
                }
            }
        }
    }

    private data class UiSelections(
        val status: ReminderStatus = ReminderStatus.FAILED,
        val query: String = "",
        val template: ReminderTemplate? = null,
        val selectedIds: Set<ReminderId> = emptySet(),
        val openedId: ReminderId? = null,
        val notice: QueueNotice? = null,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The filters a result was produced for, so an empty list can explain *why* it is empty. */
private data class QueueQuery(
    val status: ReminderStatus,
    val query: String,
    val template: ReminderTemplate?,
)

private fun AppResult<List<Reminder>>.toContent(query: QueueQuery): ReminderQueueContent =
    when (this) {
        is AppResult.Failure -> ReminderQueueContent.Failed(error.message)

        is AppResult.Success -> when {
            value.isNotEmpty() -> ReminderQueueContent.Loaded(value)

            else -> ReminderQueueContent.Empty(
                status = query.status,
                isFiltered = query.query.isNotBlank() || query.template != null,
            )
        }
    }

private fun AppResult<List<Reminder>>.idsOrEmpty(): Set<ReminderId> =
    (this as? AppResult.Success)?.value?.map { it.id }?.toSet() ?: emptySet()

private fun AppResult<List<Reminder>>.find(id: ReminderId?): Reminder? =
    id?.let { wanted -> (this as? AppResult.Success)?.value?.firstOrNull { it.id == wanted } }
