package com.anfas.feature.members

import com.anfas.core.model.Member
import com.anfas.core.model.SubscriptionTerm
import com.anfas.core.model.TermProgress

/**
 * The member profile screen.
 *
 * [Missing] is its own case rather than a null member: `observeMember` emits null when the row
 * is gone, and a member deleted on another device should say so rather than render an empty
 * shell of a profile.
 */
sealed interface MemberProfileContent {
    data object Loading : MemberProfileContent

    data class Loaded(
        val member: Member,
        /** Null when the member has never had a subscription, which is a normal state. */
        val term: SubscriptionTerm?,
        /** Null exactly when [term] is. */
        val progress: TermProgress?,
    ) : MemberProfileContent

    data object Missing : MemberProfileContent

    data class Failed(val message: String) : MemberProfileContent
}

data class MemberProfileState(
    val content: MemberProfileContent = MemberProfileContent.Loading,
    /** Whether this session holds `Permission.VIEW_THERAPY`. */
    val mayViewTherapy: Boolean = false,
    /**
     * Whether this session holds `Permission.EDIT_MEMBERS`, which gates the WhatsApp consent
     * toggle. Shown-but-disabled rather than hidden, unlike the Therapy button: consent is a fact
     * about the member that everyone who can see the profile should be able to read, even when
     * they may not change it.
     */
    val mayEdit: Boolean = false,
) {
    val member: Member? get() = (content as? MemberProfileContent.Loaded)?.member

    /**
     * Renewal is offered even with no current term — that is precisely when someone needs one.
     * It is withheld only while the profile is still loading or the member has gone.
     */
    val canRenew: Boolean get() = content is MemberProfileContent.Loaded
}
