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

/**
 * There is no "unpublish" — see `AnnouncementRepository.publish`'s KDoc — so this earns a
 * confirmation the same way `CloseCaseConfirmDialog` does for a therapy case. [reach] is the
 * live count for [form]'s audience, which is why it must never go stale — see the KDoc on the
 * `state.liveReach` combine leg in `AnnouncementsComponent`.
 */
@Composable
internal fun PublishConfirmDialog(
    form: AnnouncementForm,
    reach: Int?,
    component: AnnouncementsComponent,
    s: AppStrings,
) {
    AnfasDialog(
        title = s.announcements.publishConfirmTitle,
        onDismissRequest = component::onPublishDismissed,
        closeContentDescription = s.common.close,
        footer = {
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onPublishDismissed)
            AnfasPrimaryButton(
                text = s.announcements.publish,
                onClick = component::onPublishConfirmed,
            )
        },
    ) {
        Column {
            Text(
                text = s.announcements.publishConfirmMessage(reach ?: 0),
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
