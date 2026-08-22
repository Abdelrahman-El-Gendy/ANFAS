package com.anfas.feature.auth

import com.anfas.core.auth.SignInResult
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Staff sign-in, and first-run setup when there is no account to sign in to.
 *
 * The mode is decided by asking the repository, not by a flag the caller passes: an install that
 * has just migrated to schema v6 has zero staff rows, and showing it a login it cannot pass would
 * brick the app. Nothing invents a default account, because an app shipping with a default
 * password ships with a published one.
 *
 * There is no "forgot password" and the design's link is not built. It would need an email or SMS
 * round trip and there is no server — an owner resetting a staff password from their own account
 * is the offline equivalent, and belongs with staff management.
 */
class SignInComponent(
    componentContext: ComponentContext,
    private val repository: AuthRepository,
    dispatchers: AppDispatchers,
    private val onSignedIn: () -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("SignIn"))

    private val ui = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = ui.asStateFlow()

    init {
        // Re-evaluated on every sign-out, not once at construction.
        //
        // This component is created eagerly by the shell and outlives many sessions, so deciding
        // the mode in init alone was wrong: it was built before any account existed, latched
        // FirstRun, and still said "Set up this device" after an owner had been created and
        // signed out again. Submitting would have self-healed via AlreadyInitialised, but the
        // user was shown the wrong form.
        //
        // Resetting the fields is the other half. On a shared reception device the previous
        // person's name and username must not be sitting in the form for whoever signs in next.
        scope.launch {
            repository.observeSession().collect { session ->
                if (session == null) refreshMode()
            }
        }
    }

    private suspend fun refreshMode() {
        val mode = when (val result = repository.hasAnyAccount()) {
            is AppResult.Failure -> {
                ui.update { it.copy(error = SignInError.Unexpected(result.error.message)) }
                SignInMode.SignIn
            }

            is AppResult.Success -> if (result.value) SignInMode.SignIn else SignInMode.FirstRun
        }
        ui.value = SignInState(mode = mode)
    }

    // Typing clears the previous failure. Leaving a red message under a field someone is actively
    // correcting reads as though the new value is also wrong.
    fun onUsernameChanged(value: String) = ui.update {
        it.copy(username = value, error = null, problems = emptySet())
    }

    fun onPasswordChanged(value: String) = ui.update {
        it.copy(password = value, error = null, problems = emptySet())
    }

    fun onDisplayNameChanged(value: String) = ui.update {
        it.copy(displayName = value, error = null, problems = emptySet())
    }

    fun onTogglePasswordVisible() = ui.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val current = ui.value
        if (!current.canSubmit) return
        ui.update { it.copy(isSubmitting = true, error = null, problems = emptySet()) }

        scope.launch {
            when (current.mode) {
                SignInMode.FirstRun -> createOwner(current)
                else -> signIn(current)
            }
        }
    }

    private suspend fun signIn(current: SignInState) {
        when (val result = repository.signIn(current.username, current.password)) {
            is AppResult.Failure -> fail(SignInError.Unexpected(result.error.message))

            is AppResult.Success -> when (result.value) {
                is SignInResult.Success -> {
                    // The password is dropped from state the moment it is no longer needed, so it
                    // is not sitting in a retained state object for the life of the process.
                    ui.update { it.copy(isSubmitting = false, password = "") }
                    onSignedIn()
                }

                SignInResult.InvalidCredentials -> fail(SignInError.InvalidCredentials)

                SignInResult.AccountDisabled -> fail(SignInError.AccountDisabled)

                // Someone deleted the last account while this screen was open.
                SignInResult.NoAccounts -> ui.update {
                    it.copy(isSubmitting = false, mode = SignInMode.FirstRun, password = "")
                }
            }
        }
    }

    private suspend fun createOwner(current: SignInState) {
        val result = repository.createFirstOwner(
            username = current.username,
            password = current.password,
            displayName = current.displayName,
        )
        when (result) {
            is AppResult.Failure -> fail(SignInError.Unexpected(result.error.message))

            is AppResult.Success -> when (val outcome = result.value) {
                is CreateAccountOutcome.Created -> {
                    ui.update { it.copy(isSubmitting = false, password = "") }
                    onSignedIn()
                }

                is CreateAccountOutcome.Rejected -> ui.update {
                    it.copy(isSubmitting = false, problems = outcome.problems)
                }

                // Set up on another device or window since this screen opened. Fall back to
                // signing in rather than reporting an error the user cannot act on.
                CreateAccountOutcome.AlreadyInitialised -> ui.update {
                    it.copy(isSubmitting = false, mode = SignInMode.SignIn, password = "")
                }
            }
        }
    }

    private fun fail(error: SignInError) = ui.update {
        // The password is cleared on failure too: a retry should be retyped, and a wrong value
        // left in the field invites hammering the same one.
        it.copy(isSubmitting = false, error = error, password = "")
    }
}
