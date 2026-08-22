package com.anfas.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * Who is signed in on this device, and what they may do.
 *
 * [userId] is a [StaffAccount] id, not a `MemberId`. The inert scaffolding this replaces used
 * `MemberId`, which conflated two different things: a receptionist is not a gym member, and a
 * gym member who also coaches would have had one identity doing both jobs.
 */
data class Session(val userId: String, val roles: Set<Role>) {
    fun canAny(vararg permitted: Role): Boolean = permitted.any { it in roles }

    val isOwner: Boolean get() = Role.Owner in roles
}

/**
 * Sourced from the export's own vocabulary — `staff-login`, `therapy-case-file` and
 * `permission-denied` between them name an owner, an admin, a therapist, a coach and a
 * receptionist. Members do not sign in to this app; the [Member] role exists for a future
 * member-facing surface and grants nothing today.
 */
enum class Role {
    Owner,
    Admin,
    Therapist,
    Coach,
    Receptionist,
    Member,
}

/**
 * Where the current session lives between launches.
 *
 * [observe] rather than only a one-shot read, because the app shell gates every screen on it: a
 * sign-out has to move the whole app back to the login screen, and polling for that would race
 * the navigation.
 */
interface SessionStore {
    fun observe(): Flow<Session?>

    suspend fun current(): Session?

    suspend fun save(session: Session)

    suspend fun clear()
}
