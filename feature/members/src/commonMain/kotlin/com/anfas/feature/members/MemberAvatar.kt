package com.anfas.feature.members

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.model.Member

/**
 * Photo when there is one, initials when there isn't — the export shows both.
 *
 * Lives in the feature rather than `:core:designsystem` because it takes a [Member], and the
 * design system must not know domain types. Shared across the directory and the profile, which
 * need it at 32dp and 64dp respectively, so the size is a parameter.
 */
@Composable
internal fun MemberAvatar(member: Member, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = member.initials,
            // The larger avatar needs a larger glyph, or a 64dp circle reads as an empty ring.
            style = if (size >= LARGE_AVATAR) {
                AnfasTheme.textStyles.headlineSmall
            } else {
                AnfasTheme.textStyles.labelCaps
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LARGE_AVATAR = 48.dp
