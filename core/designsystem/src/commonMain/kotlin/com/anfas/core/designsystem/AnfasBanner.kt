package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A slim, persistent, full-width status bar for a condition that is *ongoing* — the design's
 * `offline-banner` sits directly under the top bar and stays there until the condition clears.
 *
 * Distinct from [AnfasCallout], which is a titled block explaining a single failure and its
 * remedy, and from a snackbar, which is transient. The distinction matters: a banner must be
 * legible at a glance without stealing a row of the table beneath it, so it is one line and it
 * does not wrap.
 *
 * [onDismiss] is optional and should be left null for a condition the user cannot act on.
 * A dismissible banner for something still true just teaches people to dismiss it.
 */
@Composable
fun AnfasBanner(
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Informational,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        BannerTone.Informational -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        BannerTone.Warning -> scheme.primaryContainer.copy(alpha = 0.22f) to scheme.primary
        BannerTone.Critical -> scheme.errorContainer to scheme.error
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = AnfasTheme.textStyles.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null && dismissLabel != null) {
            AnfasTextAction(text = dismissLabel, onClick = onDismiss)
        }
    }
}

enum class BannerTone { Informational, Warning, Critical }
