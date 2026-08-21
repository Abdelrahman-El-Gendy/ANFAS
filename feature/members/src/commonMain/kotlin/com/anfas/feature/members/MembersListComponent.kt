package com.anfas.feature.members

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.MemberRepository
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

/**
 * The members directory.
 *
 * Search is debounced and driven through [flatMapLatest], so typing cancels the previous
 * query's Flow instead of racing it — without that, results can arrive out of order and the
 * list flickers back to a stale term.
 *
 * Navigation is a callback, not something this component performs. Features do not own the
 * router; :composeApp does.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MembersListComponent(
    componentContext: ComponentContext,
    private val repository: MemberRepository,
    dispatchers: AppDispatchers,
    private val onMemberClicked: (MemberId) -> Unit,
    private val onAddMemberClicked: () -> Unit,
    private val onScanSheetClicked: () -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("MembersList"))

    private val query = MutableStateFlow("")

    val state: StateFlow<MembersListState> =
        combine(
            query,
            query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .flatMapLatest { term -> repository.observeMembers(term).map { term to it } },
        ) { typed, (searchedTerm, result) ->
            MembersListState(query = typed, content = result.toContent(searchedTerm))
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MembersListState(),
        )

    fun onQueryChanged(value: String) = query.update { value }

    fun onClearSearch() = query.update { "" }

    fun onMemberSelected(id: MemberId) = onMemberClicked(id)

    fun onAddMember() = onAddMemberClicked()

    fun onScanSheet() = onScanSheetClicked()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L

        /**
         * Outlives a configuration change so rotating the screen does not re-query, but not so
         * long that a backgrounded app keeps a database Flow open.
         */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppResult<List<com.anfas.core.model.Member>>.toContent(
    query: String,
): MembersListContent = when (this) {
    is AppResult.Failure -> MembersListContent.Failed(error.message)

    is AppResult.Success -> when {
        value.isNotEmpty() -> MembersListContent.Loaded(value)

        // A blank query returning nothing means the directory itself is empty; a non-blank
        // one means this search found nothing. The design draws those differently.
        query.isBlank() -> MembersListContent.DirectoryEmpty

        else -> MembersListContent.NoMatches(query)
    }
}
