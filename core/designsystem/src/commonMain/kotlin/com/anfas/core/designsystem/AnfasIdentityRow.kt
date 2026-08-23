package com.anfas.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Avatar, name and one line of supporting text — the block the export puts at the foot of the
 * desktop rail ("Staff Profile / Reception").
 *
 * Not interactive. The export's version is an anchor, but the only thing it could lead to is a
 * profile screen for the signed-in member of staff, and there is none: an account is a username,
 * a display name, roles and a password verifier, all of which are already visible here or on the
 * staff screen. A row that looks tappable and does nothing is worse than a row that does not.
 */
@Composable
fun AnfasIdentityRow(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnfasAvatar(initials = initialsOf(name))
        Column {
            Text(
                text = name,
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // A 256dp rail minus the avatar leaves little room, and a staff member may well
                // have a long name. Ellipsis rather than wrapping: wrapping changes the footer's
                // height and pushes the sign-out row about.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AnfasTheme.textStyles.labelCaps,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
