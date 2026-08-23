package com.anfas.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One entry in an [AnfasOverflowMenu].
 *
 * [icon] is optional because the menu holds two kinds of thing — destinations, which read better
 * with a glyph, and account actions like signing out, which do not need one.
 */
data class MenuAction(val label: String, val onClick: () -> Unit, val icon: ImageVector? = null)

/**
 * The trailing "⋮" of the compact top bar: account-level actions that are **not** navigation
 * destinations and so have no business taking a slot in the bottom bar.
 *
 * Dismissal is owned here rather than by the caller, and every item closes the menu before it
 * acts. A caller that forgot would leave the menu floating over the screen it just navigated to.
 */
@Composable
fun AnfasOverflowMenu(
    actions: List<MenuAction>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    initials: String? = null,
) {
    // Nothing to show. Rendering an icon button that opens an empty popup is worse than no
    // button: it reads as broken rather than as absent.
    if (actions.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // The export's compact header opens the account from an avatar, not a "⋮", and that is
        // worth following: it also answers "who is signed in" without spending a row on it.
        // Falls back to the overflow glyph while the account read is still in flight, so the
        // button never appears late.
        if (initials.isNullOrEmpty()) {
            AnfasIconButton(
                icon = AnfasIcons.MoreVert,
                contentDescription = contentDescription,
                onClick = { expanded = true },
            )
        } else {
            val label = contentDescription
            IconButton(
                onClick = { expanded = true },
                // The description goes on the button, not on the avatar: the initials are
                // decoration, and a screen reader announcing "FO" tells nobody anything.
                modifier = Modifier.semantics { this.contentDescription = label },
            ) {
                // The design's `w-9 h-9`, inside a 48dp button so the tap target still clears
                // both platforms' minimum.
                AnfasAvatar(initials = initials, size = 36.dp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = AnfasShapes.base,
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            style = AnfasTheme.textStyles.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingIcon = action.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
