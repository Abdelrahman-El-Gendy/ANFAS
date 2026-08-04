package com.anfas.core.auth

import com.anfas.core.model.MemberId

/**
 * Session and RBAC scaffolding. Role definitions and permission checks are a separate task;
 * this only fixes where they will live.
 */
data class Session(
    val userId: MemberId,
    val roles: Set<Role>,
)

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
