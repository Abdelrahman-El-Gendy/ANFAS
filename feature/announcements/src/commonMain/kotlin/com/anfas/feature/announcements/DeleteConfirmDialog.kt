package com.anfas.feature.announcements

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.AppStrings

@Composable
internal fun DeleteConfirmDialog(component: AnnouncementsComponent, s: AppStrings) {
    AnfasDialog(
        title = s.announcements.deleteConfirmTitle,
        onDismissRequest = component::onDeleteDismissed,
        closeContentDescription = s.common.close,
        footer = {
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onDeleteDismissed)
            AnfasPrimaryButton(
                text = s.announcements.deleteDraft,
                onClick = component::onDeleteConfirmed,
            )
        },
    ) {
        Column {
            Text(
                text = s.announcements.deleteConfirmMessage,
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
