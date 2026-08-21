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
    val query: String = "",
    val content: MembersListContent = MembersListContent.Loading,
)
