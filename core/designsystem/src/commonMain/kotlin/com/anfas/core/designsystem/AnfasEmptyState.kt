package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Centred nothing-state: a 64dp circular icon badge, a headline, a line of guidance, and up to
 * two actions.
 *
 * The design uses two visually distinct variants and the difference is meaningful, so it is a
 * parameter rather than a guess: [Tone.Invitation] tints the badge amber for "nothing here
 * yet, do something" (`members-list-empty`), while [Tone.Informational] keeps it grey for
 * "your query found nothing" (`search-no-results`), where there is nothing to create.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AnfasEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Informational,
    primaryAction: EmptyStateAction? = null,
    secondaryAction: EmptyStateAction? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val badgeFill = when (tone) {
        Tone.Invitation -> scheme.surfaceContainerHighest
        Tone.Informational -> scheme.surfaceContainer
    }
    val iconTint = when (tone) {
        Tone.Invitation -> scheme.primaryContainer.copy(alpha = 0.70f)
        Tone.Informational -> scheme.outline
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(badgeFill, CircleShape)
                .border(1.dp, scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = iconTint,
            )
        }
        Text(
            text = title,
            style = AnfasTheme.textStyles.headlineMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = message,
            style = AnfasTheme.textStyles.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 420.dp),
        )
        if (primaryAction != null || secondaryAction != null) {
            // FlowRow, not Row: side by side these two buttons competed for a phone's width and
            // the loser wrapped its label onto a second line at a different height from its
            // neighbour. Flowing lets them stack on a narrow screen and stay inline on a wide
            // one, which is what the export shows at each form factor.
            FlowRow(
                modifier = Modifier.padding(top = 32.dp).widthIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                secondaryAction?.let {
                    AnfasSecondaryButton(text = it.label, onClick = it.onClick, icon = it.icon)
                }
                primaryAction?.let {
                    AnfasPrimaryButton(text = it.label, onClick = it.onClick, icon = it.icon)
                }
            }
        }
    }
}

enum class Tone { Invitation, Informational }

data class EmptyStateAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
)
