package com.anfas.feature.auth

import com.anfas.core.auth.CredentialProblem
import com.anfas.core.auth.Role
import com.anfas.core.auth.StaffAccount

sealed interface StaffListContent {
    data object Loading : StaffListContent

    data class Loaded(val staff: List<StaffAccount>) : StaffListContent

    data class Failed(val message: String) : StaffListContent
}

/** Which dialog is open, if any. Sealed so the form's fields cannot exist without a target. */
sealed interface StaffDialog {
    /** Adding someone new. */
    data class Add(
        val username: String = "",
        val displayName: String = "",
        val password: String = "",
        val roles: Set<Role> = setOf(Role.Receptionist),
        val problems: Set<CredentialProblem> = emptySet(),
    ) : StaffDialog

    /** Resetting an existing account's password. Carries who, so the id cannot drift. */
    data class ResetPassword(
        val accountId: String,
        val displayName: String,
        val password: String = "",
        val problems: Set<CredentialProblem> = emptySet(),
    ) : StaffDialog
}

/** Typed like every other notice in this app, so the screen owns the wording. */
sealed interface StaffNotice {
    data class Created(val displayName: String) : StaffNotice

    data object PasswordReset : StaffNotice

    data class EnabledChanged(val enabled: Boolean) : StaffNotice

    /** Refused because it would leave nobody able to administer the device. */
    data object WouldLockOutDevice : StaffNotice

    /** Deleted on another device, or a stale id. Typed rather than a hardcoded sentence. */
    data object AccountGone : StaffNotice

    data class Failed(val message: String) : StaffNotice
}

data class StaffListState(
    val content: StaffListContent = StaffListContent.Loading,
    val dialog: StaffDialog? = null,
    val isSubmitting: Boolean = false,
    val notice: StaffNotice? = null,
    /** The signed-in account, so the list can mark it and never offer self-disable. */
    val currentUserId: String? = null,
) {
    val staff: List<StaffAccount>
        get() = (content as? StaffListContent.Loaded)?.staff.orEmpty()

    /**
     * Roles the Add dialog offers. Owner is absent because the owner is established once at first
     * run — the repository strips it too, so this is the UI half of one rule.
     */
    val assignableRoles: List<Role> get() = Role.entries - Role.Owner - Role.Member
}
