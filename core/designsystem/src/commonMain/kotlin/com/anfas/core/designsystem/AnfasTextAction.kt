package com.anfas.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A text-only action — table row actions ("Retry", "Open member") and dialog footers
 * ("Copy error", "Open WhatsApp").
 *
 * [emphasis] separates the primary action in a group from the secondary ones: the export draws
 * "Retry" in amber and "Open member" in muted grey, side by side.
 */
enum class TextActionEmphasis { Primary, Muted }

@Composable
fun AnfasTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasis: TextActionEmphasis = TextActionEmphasis.Primary,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when (emphasis) {
        TextActionEmphasis.Primary -> scheme.primary
        TextActionEmphasis.Muted -> scheme.onSurfaceVariant
    }
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) color else color.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = AnfasTheme.textStyles.bodyMedium,
                color = if (enabled) color else color.copy(alpha = 0.38f),
            )
        }
    }
}
