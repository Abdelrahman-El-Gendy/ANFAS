package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
            // Side by side these two buttons competed for a phone's width and the loser wrapped
            // its label onto a second line at a different height from its neighbour. A plain
            // FlowRow fixed the wrapping but left two stacked buttons of *different* widths,
            // which reads as a mistake rather than a choice — so the decision is explicit:
            // inline when they fit, full-width and stacked when they don't.
            BoxWithConstraints(
                modifier = Modifier.padding(top = 32.dp).widthIn(max = ACTIONS_MAX_WIDTH),
            ) {
                val stacked = maxWidth < ACTIONS_INLINE_MIN_WIDTH
                if (stacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Primary first when stacked: the top button is the one a thumb reaches
                        // and the one the empty state is inviting.
                        primaryAction?.let {
                            AnfasPrimaryButton(
                                text = it.label,
                                onClick = it.onClick,
                                icon = it.icon,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        secondaryAction?.let {
                            AnfasSecondaryButton(
                                text = it.label,
                                onClick = it.onClick,
                                icon = it.icon,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterHorizontally,
                        ),
                    ) {
                        secondaryAction?.let {
                            AnfasSecondaryButton(
                                text = it.label,
                                onClick = it.onClick,
                                icon = it.icon,
                            )
                        }
                        primaryAction?.let {
                            AnfasPrimaryButton(
                                text = it.label,
                                onClick = it.onClick,
                                icon = it.icon,
                            )
                        }
                    }
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

/**
 * Below this the two action buttons cannot sit side by side without one of them wrapping its
 * label. Measured against the widest real pairing — "Add member" with its icon next to
 * "Scan sheet" — which needs about 340dp plus the empty state's own 32dp padding either side.
 */
private val ACTIONS_INLINE_MIN_WIDTH = 404.dp

private val ACTIONS_MAX_WIDTH = 420.dp
