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
 * Back affordance and a title, for a screen reached by a **push** rather than by the nav bar.
 *
 * Every pushed screen needs one, and that is a navigational requirement rather than decoration:
 * a destination with no tab of its own and no back button can only be left by the system back
 * gesture, which desktop does not have at all.
 *
 * [backContentDescription] is required because the arrow carries no visible label — and the icon
 * is declared with `autoMirror`, so it points the correct way in Arabic without the caller
 * doing anything.
 */
@Composable
fun AnfasDetailTopBar(
    title: String,
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnfasIconButton(
                icon = AnfasIcons.ArrowBack,
                contentDescription = backContentDescription,
                onClick = onBack,
            )
            Text(
                text = title,
                style = AnfasTheme.textStyles.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Weighted so a long title yields to the actions instead of pushing them off
                // the trailing edge -- the same defect the tab indicator and the intake footer
                // had.
                modifier = Modifier.weight(1f),
            )
            actions?.invoke()
        }
        AnfasTableDivider()
    }
}
