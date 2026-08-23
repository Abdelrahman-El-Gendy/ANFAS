package com.anfas.feature.members

import com.anfas.core.model.Member

/**
 * What the directory screen is showing.
 *
 * The nothing-states are separate cases rather than an empty list plus flags because the
 * design draws them completely differently: [DirectoryEmpty] invites you to add or scan,
 * [NoMatches] echoes the query back and offers only "clear search". Collapsing them would
 * lose that distinction at the call site.
 */
sealed interface MembersListContent {
    data object Loading : MembersListContent
    data class Loaded(val members: List<Member>) : MembersListContent
    data object DirectoryEmpty : MembersListContent
    data class NoMatches(val query: String) : MembersListContent
    data class Failed(val message: String) : MembersListContent
}

/**
 * [query] is held outside [content] on purpose: the text field must keep the characters the
 * user typed while a debounced search is still resolving, so it cannot live in a state that
 * gets replaced by [MembersListContent.Loading].
 */
data class MembersListState(
    /** Non-null while the add form is open. */
    val addForm: AddMemberForm? = null,
    val notice: MembersNotice? = null,
    /** Whether this session holds `Permission.EDIT_MEMBERS`. */
    val mayEditMembers: Boolean = false,
    /**
     * Whether this session holds `Permission.SCAN_INTAKE`.
     *
     * Intake is entered from this screen rather than from the nav bar, so this screen is now the
     * only place the permission is expressed in the UI. A coach holds it; a therapist does not.
     */
    val mayScanIntake: Boolean = false,
    val query: String = "",
    val content: MembersListContent = MembersListContent.Loading,
)

/**
 * The manual add form, as state rather than a remembered composable value, so a rotation or a
 * process death does not lose a half-typed name.
 *
 * [nameError] is a flag, not a message: the screen owns the wording, as everywhere else here.
 */
data class AddMemberForm(
    val fullName: String = "",
    val phone: String = "",
    val isSubmitting: Boolean = false,
    val nameError: Boolean = false,
) {
    /**
     * A name is the only requirement. A walk-in can be registered without a phone, and demanding
     * one at the desk is how staff end up typing 000.
     */
    val canSubmit: Boolean get() = !isSubmitting && fullName.isNotBlank()
}

/** Typed like every other notice here. */
sealed interface MembersNotice {
    data class Added(val name: String, val membershipNumber: String) : MembersNotice

    data class Failed(val message: String) : MembersNotice
}
