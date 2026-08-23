package com.anfas.feature.members

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasAvatar
import com.anfas.core.model.Member

/**
 * A member's avatar. Initials, not a photograph: nothing in the app uploads one.
 *
 * Lives in the feature rather than `:core:designsystem` because it takes a [Member], and the
 * design system must not know domain types. Shared across the directory and the profile, which
 * need it at 32dp and 64dp respectively, so the size is a parameter.
 */
@Composable
internal fun MemberAvatar(member: Member, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    // Delegates to the design system's circle, which the rail footer and the compact top bar
    // also use. Kept as a named wrapper because the *choice* of what to put in a member's avatar
    // is this feature's business, not the design system's.
    AnfasAvatar(initials = member.initials, size = size, modifier = modifier)
}
