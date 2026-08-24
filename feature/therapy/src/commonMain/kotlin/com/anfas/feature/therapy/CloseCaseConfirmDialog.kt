package com.anfas.feature.therapy

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.AppStrings

/**
 * A confirmation, not a silent action — closing is reversible in spirit (a new case can always
 * be opened) but not in fact (this exact record cannot be reopened), so it earns a step.
 */
@Composable
internal fun CloseCaseConfirmDialog(component: TherapyComponent, s: AppStrings) {
    AnfasDialog(
        title = s.therapy.closeCaseConfirmTitle,
        onDismissRequest = component::onCloseCaseDismissed,
        closeContentDescription = s.common.close,
        footer = {
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onCloseCaseDismissed)
            AnfasPrimaryButton(
                text = s.therapy.closeCase,
                onClick = component::onCloseCaseConfirmed,
            )
        },
    ) {
        Column {
            Text(
                text = s.therapy.closeCaseConfirmMessage,
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
