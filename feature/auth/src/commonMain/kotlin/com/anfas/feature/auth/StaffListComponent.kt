package com.anfas.feature.auth

import com.anfas.core.auth.Role
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.StaffChangeOutcome
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

/**
 * Staff management, reachable only with `Permission.MANAGE_STAFF`.
 *
 * The permission is enforced by the shell before this screen is composed, not here — a component
 * that also checks would be a second place for the rule to drift. What this component does own is
 * the rules that are about *these* operations: no self-disable, and no second owner.
 */
class StaffListComponent(
    componentContext: ComponentContext,
    private val repository: AuthRepository,
    dispatchers: AppDispatchers,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("StaffList"))

    private val ui = MutableStateFlow(UiState())

    val state: StateFlow<StaffListState> = combine(
        ui,
        repository.observeStaff(),
        repository.observeSession(),
    ) { local, staffResult, session ->
        StaffListState(
            content = when (staffResult) {
                is AppResult.Failure -> StaffListContent.Failed(staffResult.error.message)
                is AppResult.Success -> StaffListContent.Loaded(staffResult.value)
            },
            dialog = local.dialog,
            isSubmitting = local.isSubmitting,
            notice = local.notice,
            currentUserId = session?.userId,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StaffListState(),
    )

    fun onAddStaff() = ui.update { it.copy(dialog = StaffDialog.Add(), notice = null) }

    fun onResetPassword(accountId: String, displayName: String) = ui.update {
        it.copy(
            dialog = StaffDialog.ResetPassword(accountId = accountId, displayName = displayName),
            notice = null,
        )
    }

    fun onDismissDialog() = ui.update { it.copy(dialog = null) }

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    fun onAddFieldChanged(
        username: String? = null,
        displayName: String? = null,
        password: String? = null,
    ) = ui.update { current ->
        val dialog = current.dialog as? StaffDialog.Add ?: return@update current
        current.copy(
            dialog = dialog.copy(
                username = username ?: dialog.username,
                displayName = displayName ?: dialog.displayName,
                password = password ?: dialog.password,
                // Typing clears the previous rejection, so a corrected field stops looking wrong.
                problems = emptySet(),
            ),
        )
    }

    fun onResetFieldChanged(password: String) = ui.update { current ->
        val dialog = current.dialog as? StaffDialog.ResetPassword ?: return@update current
        current.copy(dialog = dialog.copy(password = password, problems = emptySet()))
    }

    /** At least one role, always: an account with none can sign in and do nothing. */
    fun onRoleToggled(role: Role) = ui.update { current ->
        val dialog = current.dialog as? StaffDialog.Add ?: return@update current
        val next = if (role in dialog.roles) dialog.roles - role else dialog.roles + role
        current.copy(dialog = dialog.copy(roles = next.ifEmpty { dialog.roles }))
    }

    fun onSubmitDialog() {
        val dialog = ui.value.dialog ?: return
        if (ui.value.isSubmitting) return
        ui.update { it.copy(isSubmitting = true) }

        scope.launch {
            when (dialog) {
                is StaffDialog.Add -> submitAdd(dialog)
                is StaffDialog.ResetPassword -> submitReset(dialog)
            }
        }
    }

    fun onToggleEnabled(accountId: String, enabled: Boolean) {
        scope.launch {
            when (val result = repository.setStaffEnabled(accountId, enabled)) {
                is AppResult.Failure -> notify(StaffNotice.Failed(result.error.message))

                is AppResult.Success -> when (result.value) {
                    StaffChangeOutcome.Changed -> notify(StaffNotice.EnabledChanged(enabled))

                    StaffChangeOutcome.WouldLockOutDevice ->
                        notify(StaffNotice.WouldLockOutDevice)

                    // Deleted on another device. The list re-reads itself, so saying nothing more
                    // than "failed" is honest and the row is about to disappear anyway.
                    StaffChangeOutcome.NotFound -> notify(StaffNotice.AccountGone)

                    is StaffChangeOutcome.Rejected -> notify(StaffNotice.AccountGone)
                }
            }
        }
    }

    private suspend fun submitAdd(dialog: StaffDialog.Add) {
        val result = repository.createStaff(
            username = dialog.username,
            password = dialog.password,
            displayName = dialog.displayName,
            roles = dialog.roles,
        )
        when (result) {
            is AppResult.Failure -> {
                ui.update { it.copy(isSubmitting = false) }
                notify(StaffNotice.Failed(result.error.message))
            }

            is AppResult.Success -> when (val outcome = result.value) {
                is CreateAccountOutcome.Created -> ui.update {
                    // The password leaves state with the dialog.
                    it.copy(
                        isSubmitting = false,
                        dialog = null,
                        notice = StaffNotice.Created(dialog.displayName.trim()),
                    )
                }

                is CreateAccountOutcome.Rejected -> ui.update {
                    it.copy(
                        isSubmitting = false,
                        dialog = dialog.copy(problems = outcome.problems, password = ""),
                    )
                }

                CreateAccountOutcome.AlreadyInitialised -> ui.update {
                    // Cannot happen from here — createStaff does not gate on first run — but the
                    // outcome type is shared, so it is handled rather than ignored.
                    it.copy(isSubmitting = false, dialog = null)
                }
            }
        }
    }

    private suspend fun submitReset(dialog: StaffDialog.ResetPassword) {
        when (val result = repository.resetStaffPassword(dialog.accountId, dialog.password)) {
            is AppResult.Failure -> {
                ui.update { it.copy(isSubmitting = false) }
                notify(StaffNotice.Failed(result.error.message))
            }

            is AppResult.Success -> when (val outcome = result.value) {
                StaffChangeOutcome.Changed -> ui.update {
                    it.copy(
                        isSubmitting = false,
                        dialog = null,
                        notice = StaffNotice.PasswordReset,
                    )
                }

                is StaffChangeOutcome.Rejected -> ui.update {
                    it.copy(
                        isSubmitting = false,
                        dialog = dialog.copy(problems = outcome.problems, password = ""),
                    )
                }

                StaffChangeOutcome.NotFound,
                StaffChangeOutcome.WouldLockOutDevice,
                -> ui.update {
                    it.copy(
                        isSubmitting = false,
                        dialog = null,
                        notice = StaffNotice.AccountGone,
                    )
                }
            }
        }
    }

    private fun notify(notice: StaffNotice) = ui.update { it.copy(notice = notice) }

    private data class UiState(
        val dialog: StaffDialog? = null,
        val isSubmitting: Boolean = false,
        val notice: StaffNotice? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
