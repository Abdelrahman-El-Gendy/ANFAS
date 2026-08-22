package com.anfas.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun AnfasBottomNav(items: List<NavItem>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        AnfasTableDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer)
                // Inset AFTER the background, so the bar's surface paints into the gesture area
                // and only its contents are held clear of the home indicator. Insetting the bar
                // itself instead leaves it floating above a strip of bare background, which on
                // iOS reads as a layout mistake -- a tab bar is expected to meet the screen edge.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                // weight(1f), not SpaceEvenly. Evenly spaced items are sized by their own labels,
                // so "Reminders" got a visibly larger tap target than "Intake" and every target
                // moved when the language changed. Equal columns make each destination the same
                // size and keep the bar stable between English and Arabic.
                BottomNavItem(item = item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BottomNavItem(item: NavItem, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    // Animated so a selection reads as movement rather than a jump -- 150ms is short enough that
    // switching tabs still feels instant, long enough that an accidental tap is visible.
    val contentColor by animateColorAsState(
        targetValue = if (item.selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(SELECTION_ANIMATION_MS),
        label = "navItemContent",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (item.selected) {
            scheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(SELECTION_ANIMATION_MS),
        label = "navItemIndicator",
    )

    Column(
        modifier = modifier
            // A floor, not a fixed height: it clears Material's 48dp minimum and Apple's 44pt
            // one with room for the label, so the whole cell is tappable rather than just the
            // glyph. clip() comes before selectable() so the press ripple is contained by the
            // cell instead of bleeding across the bar.
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(AnfasShapes.base)
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The indicator sits behind the icon alone, not the whole cell. A full-cell fill grows
        // and shrinks with the label, so the selected destination looked like a different size
        // of button; a pill around the icon is the same shape for all three.
        Box(
            modifier = Modifier
                .background(indicatorColor, AnfasShapes.chip)
                .padding(horizontal = 18.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                // Null on purpose: the visible label already names the destination, and a
                // description here would make a screen reader announce it twice.
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.size(3.dp))
        Text(
            text = item.label,
            style = AnfasTheme.textStyles.labelCaps,
            color = contentColor,
            textAlign = TextAlign.Center,
            // A third of a narrow phone is not much and Arabic labels run longer. Ellipsis
            // rather than wrapping: one shortened label stays legible, whereas wrapping changes
            // the bar's height and shatters the row.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** Clears Material's 48dp minimum and Apple's 44pt one with room for a label underneath. */
private val MIN_TOUCH_TARGET = 56.dp

private const val SELECTION_ANIMATION_MS = 150
