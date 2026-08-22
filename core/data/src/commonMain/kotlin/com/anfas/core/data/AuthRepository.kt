package com.anfas.core.data

import com.anfas.core.auth.CredentialProblem
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Local staff sign-in.
 *
 * There is no auth server. Accounts live in this device's database and are created by whoever
 * already holds [Role.Owner] — so the interesting states are "no accounts yet" (first run) and
 * "signed out", not "token expired".
 *
 * [observeSession] is the app's gate. It emits null when nobody is signed in, which the shell
 * turns into the login screen.
 */
interface AuthRepository {

    fun observeSession(): Flow<Session?>

    /**
     * True when the database holds no staff at all.
     *
     * The app must not show a login it is impossible to pass, so a fresh install — or an existing
     * install that has just migrated to schema v6 — offers first-run setup instead. Migration
     * deliberately does not invent an account, because an app that ships with a default password
     * ships with a published one.
     */
    suspend fun hasAnyAccount(): AppResult<Boolean>

    suspend fun signIn(username: String, password: String): AppResult<SignInResult>

    suspend fun signOut(): AppResult<Unit>

    /**
     * Creates the first Owner. Fails with [CredentialProblem]s rather than throwing, so the form
     * can mark the offending field.
     *
     * Refuses if any account already exists — otherwise anyone reaching the setup screen could
     * mint themselves an Owner on a device that is already in use.
     */
    suspend fun createFirstOwner(
        username: String,
        password: String,
        displayName: String,
    ): AppResult<CreateAccountOutcome>

    fun observeStaff(): Flow<AppResult<List<StaffAccount>>>
}

/** Typed so the form can attach each problem to its field instead of showing one sentence. */
sealed interface CreateAccountOutcome {
    data class Created(val session: Session) : CreateAccountOutcome

    data class Rejected(val problems: Set<CredentialProblem>) : CreateAccountOutcome

    /** An account already exists, so this is not a first run. */
    data object AlreadyInitialised : CreateAccountOutcome
}
