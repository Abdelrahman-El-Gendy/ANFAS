package com.anfas.core.auth

import com.anfas.core.model.MemberId

/**
 * Session and RBAC scaffolding. INTENTIONALLY INERT: there are no implementations and no
 * callers yet, and :composeApp deliberately does not depend on this module -- an unused
 * dependency edge still costs build time and R8 input. The staff-login screen is designed but
 * unbuilt; re-add the edge in the commit that first implements SessionStore.
 * Role definitions and permission checks are a separate task;
 * this only fixes where they will live.
 */
data class Session(val userId: MemberId, val roles: Set<Role>)

enum class Role {
    Owner,
    Admin,
    Therapist,
    Coach,
    Receptionist,
    Member,
}

interface SessionStore {
    suspend fun current(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}
