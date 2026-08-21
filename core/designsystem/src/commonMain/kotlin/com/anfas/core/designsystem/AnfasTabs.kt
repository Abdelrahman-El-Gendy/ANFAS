package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Underlined tab strip with count badges, as the reminder queue draws it.
 *
 * The badge is part of a tab rather than a separate slot because the count is what staff
 * actually navigate by — "4 failed" is the reason to open the tab. [Tab.emphasiseCount] tints
 * the badge with the error colour, which the export uses to make a non-zero failure count
 * impossible to miss.
 */
data class Tab(
    val label: String,
    val count: Int? = null,
    val emphasiseCount: Boolean = false,
)

@Composable
fun AnfasTabs(
    tabs: List<Tab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier.clickable { onTabSelected(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = tab.label,
                            style = AnfasTheme.textStyles.bodyMedium,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                        )
                        if (tab.count != null) {
                            CountBadge(tab.count, tab.emphasiseCount)
                        }
                    }
                    // The 2dp indicator is drawn per tab so it tracks the tab's own width
                    // instead of needing a measured offset animation.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) scheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
        AnfasTableDivider()
    }
}

@Composable
private fun CountBadge(count: Int, emphasise: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container = if (emphasise) scheme.error else scheme.surfaceContainerHighest
    val content = if (emphasise) scheme.onError else scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = formatCount(count), style = AnfasTheme.textStyles.labelCaps, color = content)
    }
}

/** Grouped, because the export shows "1,240" — an ungrouped 1240 reads as a different number. */
internal fun formatCount(count: Int): String {
    val digits = count.toString()
    if (digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString(",").reversed()
}
