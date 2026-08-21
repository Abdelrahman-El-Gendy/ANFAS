package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A top-level destination. The design draws these two ways — a bottom bar under 600dp and a
 * fixed 256dp rail above it — so both are provided and the shell picks by width.
 *
 * Only destinations that exist should be passed in. The export's sidebar lists eight, but a
 * nav entry that leads nowhere is worse than an absent one.
 */
data class NavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun AnfasBottomNav(
    items: List<NavItem>,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        AnfasTableDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier
                        .clip(AnfasShapes.base)
                        .background(
                            if (item.selected) {
                                scheme.primaryContainer.copy(alpha = 0.20f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .selectable(
                            selected = item.selected,
                            role = Role.Tab,
                            onClick = item.onClick,
                        )
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.selected) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = item.label,
                        style = AnfasTheme.textStyles.labelCaps,
                        color = if (item.selected) scheme.primary else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun AnfasNavRail(
    items: List<NavItem>,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(256.dp)
                .fillMaxHeight()
                .background(scheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title != null) {
                Column(modifier = Modifier.padding(start = 12.dp, bottom = 24.dp)) {
                    Text(
                        text = title,
                        style = AnfasTheme.textStyles.headlineSmall,
                        color = scheme.primary,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = AnfasTheme.textStyles.labelCaps,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AnfasShapes.base)
                        .background(
                            if (item.selected) {
                                scheme.primaryContainer.copy(alpha = 0.20f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .selectable(
                            selected = item.selected,
                            role = Role.Tab,
                            onClick = item.onClick,
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.selected) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = item.label,
                        style = AnfasTheme.textStyles.bodyMedium,
                        color = if (item.selected) scheme.primary else scheme.onSurface,
                    )
                }
            }
            if (footer != null) {
                Spacer(Modifier.weight(1f))
                footer()
            }
        }
        AnfasVerticalDivider()
    }
}

@Composable
private fun AnfasVerticalDivider() {
    androidx.compose.material3.VerticalDivider(
        thickness = 1.dp,
        color = AnfasTheme.colors.offWhite.copy(alpha = AnfasTheme.alphas.border),
    )
}
