package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The status pill used throughout the staff app.
 *
 * Every tone is a tinted container plus solid content plus a barely-there border, which is
 * how the export builds them (`bg-secondary-container/20 text-secondary border-secondary/10`).
 * Tones are semantic, not decorative — see [AnfasExtendedColors] on sage and rose.
 *
 * Radius is 6dp, not the 12dp base and not a pill: the export uses Tailwind's `rounded-md`
 * here. design.md's prose says status chips "may be pill-shaped", but no screen does that.
 */
enum class ChipTone { Positive, Critical, Neutral, Recovery, ClassType, Warning }

@Composable
fun AnfasStatusChip(label: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val brand = AnfasTheme.colors
    val (container, content) = when (tone) {
        ChipTone.Positive -> scheme.secondaryContainer to scheme.secondary

        ChipTone.Critical -> scheme.errorContainer to scheme.error

        ChipTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant

        ChipTone.Recovery -> brand.sage to brand.sage

        ChipTone.ClassType -> brand.rose to brand.rose

        // The export's own "Needs service" amber (`surface-tint`) -- distinct from Critical's
        // red, which is reserved for a unit that is actually out of order.
        ChipTone.Warning -> scheme.primaryContainer to scheme.primary
    }
    // Neutral is already a solid surface step, so tinting it again would wash it out.
    val containerAlpha = if (tone == ChipTone.Neutral) 1f else 0.20f

    Box(
        modifier = modifier
            .background(container.copy(alpha = containerAlpha), RoundedCornerShape(6.dp))
            .border(1.dp, content.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label, style = AnfasTheme.textStyles.labelCaps, color = content)
    }
}

// No domain->tone mapping lives here on purpose. :core:designsystem must not know about
// MembershipStatus or any other domain type; the feature that owns the field maps it.
