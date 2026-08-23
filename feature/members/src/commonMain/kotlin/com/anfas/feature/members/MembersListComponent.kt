package com.anfas.feature.members

import com.anfas.core.auth.Permission
import com.anfas.core.auth.can
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
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
import kotlinx.coroutines.launch

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
    private val auth: AuthRepository,
    dispatchers: AppDispatchers,
    private val onMemberClicked: (MemberId) -> Unit,
    private val onAddMemberClicked: () -> Unit,
    private val onScanSheetClicked: () -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("MembersList"))

    private val query = MutableStateFlow("")
    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<MembersListState> =
        combine(
            query,
            local,
            auth.observeSession(),
            query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
                .distinctUntilChanged()
                .flatMapLatest { term -> repository.observeMembers(term).map { term to it } },
        ) { typed, ui, session, (searchedTerm, result) ->
            MembersListState(
                addForm = ui.addForm,
                notice = ui.notice,
                mayEditMembers = session?.can(Permission.EDIT_MEMBERS) == true,
                mayScanIntake = session?.can(Permission.SCAN_INTAKE) == true,
                query = typed,
                content = result.toContent(searchedTerm),
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = MembersListState(),
        )

    fun onQueryChanged(value: String) = query.update { value }

    fun onClearSearch() = query.update { "" }

    fun onMemberSelected(id: MemberId) = onMemberClicked(id)

    /**
     * Opens the form rather than calling out to the router.
     *
     * Adding a member is a two-field dialog, not a destination: a pushed screen would need its
     * own route, its own permission entry and a back stack, and it would leave the directory —
     * which is where staff want to see the member appear.
     */
    fun onAddMember() {
        local.update { it.copy(addForm = AddMemberForm(), notice = null) }
        onAddMemberClicked()
    }

    fun onAddFormDismissed() = local.update { it.copy(addForm = null) }

    fun onAddNameChanged(value: String) = local.update { current ->
        val form = current.addForm ?: return@update current
        current.copy(addForm = form.copy(fullName = value, nameError = false))
    }

    fun onAddPhoneChanged(value: String) = local.update { current ->
        val form = current.addForm ?: return@update current
        current.copy(addForm = form.copy(phone = value))
    }

    fun onNoticeShown() = local.update { it.copy(notice = null) }

    fun onAddSubmit() {
        val form = local.value.addForm ?: return
        if (!form.canSubmit) {
            local.update { it.copy(addForm = form.copy(nameError = form.fullName.isBlank())) }
            return
        }
        local.update { it.copy(addForm = form.copy(isSubmitting = true)) }

        scope.launch {
            when (val result = repository.create(form.fullName, form.phone)) {
                is AppResult.Failure -> local.update {
                    it.copy(
                        addForm = form.copy(isSubmitting = false),
                        notice = MembersNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> local.update {
                    // The form closes and the directory re-reads itself, so the new member
                    // appears where staff are already looking.
                    it.copy(
                        addForm = null,
                        notice = MembersNotice.Added(
                            name = result.value.fullName,
                            membershipNumber = result.value.membershipNumber,
                        ),
                    )
                }
            }
        }
    }

    fun onScanSheet() = onScanSheetClicked()

    private data class LocalState(
        val addForm: AddMemberForm? = null,
        val notice: MembersNotice? = null,
    )

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
