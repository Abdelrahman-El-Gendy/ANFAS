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
import androidx.compose.ui.unit.dp

/**
 * Page title, one line of supporting text, and trailing actions — the block every staff screen
 * opens with ("Members" / "Manage and track membership status.").
 *
 * [Arrangement.SpaceBetween] pushes the actions to the trailing edge when the title is short, but
 * it can only distribute space that is *left over* — and the title takes `weight(1f)`, so once the
 * title is long enough to claim its whole share there is nothing left and the two land flush
 * against each other. On an iPhone that rendered "Today's schedule" touching the "Add class"
 * button with no gap at all, which reads as clipped text. [TITLE_ACTIONS_GUTTER] is padding on the
 * actions rather than an `Arrangement` gap because it has to be part of the actions' *measured*
 * width: that is what shrinks the title's weighted share, so the title wraps instead of colliding.
 */
@Composable
fun AnfasScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable (RowScopeActions.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = AnfasTheme.textStyles.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (actions != null) {
            Row(
                modifier = Modifier.padding(start = TITLE_ACTIONS_GUTTER),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RowScopeActions.actions()
            }
        }
    }
}

/**
 * The minimum breathing room between a page title and its first action. Matches the 16dp mobile
 * page margin, so the gap reads as the same rhythm as the screen's own edges.
 */
private val TITLE_ACTIONS_GUTTER = 16.dp

/** Marker receiver so callers cannot accidentally use Row weights in the actions slot. */
object RowScopeActions
