package com.anfas.feature.auth

import com.anfas.core.auth.CredentialProblem

/**
 * Which form the screen is showing.
 *
 * [FirstRun] is a separate mode rather than a variant of the login form because the two ask for
 * different things and mean different things: one authenticates against an account, the other
 * creates the only account that can create others.
 */
enum class SignInMode { Checking, SignIn, FirstRun }

/**
 * [error] is a typed failure rather than a rendered sentence, so the screen owns the copy — the
 * same reason the reminder queue and intake use typed notices.
 */
sealed interface SignInError {
    data object InvalidCredentials : SignInError

    data object AccountDisabled : SignInError

    /** Storage failed. Carries the message because only the boundary knows what went wrong. */
    data class Unexpected(val message: String) : SignInError
}

data class SignInState(
    val mode: SignInMode = SignInMode.Checking,
    val username: String = "",
    val password: String = "",
    val displayName: String = "",
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: SignInError? = null,
    /** Per-field problems from account creation, so the form can mark the offending input. */
    val problems: Set<CredentialProblem> = emptySet(),
) {
    /**
     * Only blocks on genuinely empty input. Length rules are enforced by [CredentialRules] on
     * submit, because disabling the button until a password is long enough hides *why* it is
     * disabled — and on sign-in the rule may not even apply, since an old account can predate a
     * raised minimum.
     */
    val canSubmit: Boolean
        get() = !isSubmitting && username.isNotBlank() && password.isNotEmpty() &&
            (mode != SignInMode.FirstRun || displayName.isNotBlank())

    fun problem(field: CredentialProblem): Boolean = field in problems
}
