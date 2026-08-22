package com.anfas.core.data

import com.anfas.core.auth.CredentialProblem
import com.anfas.core.auth.Permission
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

    /**
     * Creates a staff account with the given roles. Requires [Permission.MANAGE_STAFF], which the
     * caller must already have checked — this layer does not know who is asking.
     *
     * Refuses [Role.Owner] deliberately: the owner is established once, at first run. Letting the
     * owner mint a second owner is a support problem (who removes whom) with no product need, and
     * the narrower rule can be widened later if one appears.
     */
    suspend fun createStaff(
        username: String,
        password: String,
        displayName: String,
        roles: Set<Role>,
    ): AppResult<CreateAccountOutcome>

    /**
     * Switches an account off without deleting it, so its history stays attributable.
     *
     * Refuses to disable the last enabled account holding [Permission.MANAGE_STAFF] — otherwise
     * a device can be left with nobody able to turn anything back on, and there is no server to
     * recover from.
     */
    suspend fun setStaffEnabled(id: String, enabled: Boolean): AppResult<StaffChangeOutcome>

    /** Owner-driven password reset. There is no self-service reset: see SignInComponent's KDoc. */
    suspend fun resetStaffPassword(id: String, newPassword: String): AppResult<StaffChangeOutcome>
}

/** Typed like [CreateAccountOutcome] so the screen can explain a refusal rather than a failure. */
sealed interface StaffChangeOutcome {
    data object Changed : StaffChangeOutcome

    data class Rejected(val problems: Set<CredentialProblem>) : StaffChangeOutcome

    /** The account is gone — deleted on another device, or the id is stale. */
    data object NotFound : StaffChangeOutcome

    /**
     * Refused because it would leave nobody able to administer the device. Its own case rather
     * than a generic failure, because the user needs to know it is a rule and not a bug.
     */
    data object WouldLockOutDevice : StaffChangeOutcome
}

/** Typed so the form can attach each problem to its field instead of showing one sentence. */
sealed interface CreateAccountOutcome {
    data class Created(val session: Session) : CreateAccountOutcome

    data class Rejected(val problems: Set<CredentialProblem>) : CreateAccountOutcome

    /** An account already exists, so this is not a first run. */
    data object AlreadyInitialised : CreateAccountOutcome
}
