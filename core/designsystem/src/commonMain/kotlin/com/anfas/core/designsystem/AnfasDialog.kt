package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A Layer-2 modal: `surface-container-low`, a titled header with a close affordance, a
 * scrollable body and a footer action bar.
 *
 * This is the **only** thing in the design allowed a shadow — cards define themselves with a
 * border instead. The soft 8dp/40%-black drop is the design's single elevation rule.
 */
@Composable
fun AnfasDialog(
    title: String,
    closeContentDescription: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    footer: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        AnfasDialogPanel(
            title = title,
            closeContentDescription = closeContentDescription,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            icon = icon,
            iconTint = iconTint,
            footer = footer,
            content = content,
        )
    }
}

/**
 * The panel itself, without the platform dialog window around it.
 *
 * Split out so its layout can be measured: `Dialog` opens a window of its own, which ignores the
 * constraints of whatever composes it, so a test could not put the panel in a short window and
 * check that the footer survived. That is exactly the invariant worth pinning — see
 * `AnfasDialogTest`.
 */
@Composable
internal fun AnfasDialogPanel(
    title: String,
    closeContentDescription: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    footer: @Composable (RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .widthIn(max = 520.dp)
            // Every dialog here is a form, so the keyboard is the normal case rather than the
            // edge one: without this the fields at the bottom of a six-field dialog sat under
            // it on a phone, the same defect the sign-in screen had.
            .imePadding()
            .shadow(elevation = AnfasElevation.modal, shape = AnfasShapes.base)
            .clip(AnfasShapes.base)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, AnfasShapes.base),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint ?: scheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = title,
                    style = AnfasTheme.textStyles.headlineSmall,
                    color = scheme.onSurface,
                )
            }
            AnfasIconButton(
                icon = AnfasIcons.Close,
                contentDescription = closeContentDescription,
                onClick = onDismissRequest,
            )
        }
        AnfasTableDivider()
        // Actually scrollable, which the KDoc above has always claimed and this had never
        // been. `weight(1f, fill = false)` is what pins the header and the footer while the
        // body takes only the space that is left: with the body unbounded, a form taller than
        // the window pushed its own footer -- Save and Cancel -- off the bottom edge, so a
        // dialog could be impossible to either submit or dismiss. `fill = false` so a short
        // dialog still wraps its content instead of stretching to the full window height.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
        if (footer != null) {
            AnfasTableDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainer.copy(alpha = 0.30f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                content = footer,
            )
        }
    }
}
