package com.anfas.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.auth.CredentialProblem
import com.anfas.core.auth.Role
import com.anfas.core.auth.StaffAccount
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDetailTopBar
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.strings

/**
 * Staff management, for whoever holds `Permission.MANAGE_STAFF`.
 *
 * Not in the Stitch export — there is no `staff-management` screen — so this follows the
 * conventions of the screens that are: a screen header, a card holding rows, typed notices, and
 * dialogs for the two write operations. What it deliberately does not offer is deletion: an
 * account's history should stay attributable, so it is disabled instead.
 */
@Composable
fun StaffListScreen(component: StaffListComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(modifier = modifier.fillMaxSize()) {
        // Pushed from the account menu, so it owns its way back. Outside the padded body Column
        // deliberately: a top bar's divider spans the screen, and its back button should sit on
        // the screen's edge rather than indented by the content margin.
        AnfasDetailTopBar(
            title = s.staff.title,
            onBack = component::onBack,
            backContentDescription = s.common.back,
        )
        Body(component = component, state = state, s = s)
    }

    when (val dialog = state.dialog) {
        null -> Unit
        is StaffDialog.Add -> AddStaffDialog(dialog, state, component, s)
        is StaffDialog.ResetPassword -> ResetPasswordDialog(dialog, state, component, s)
    }
}

@Composable
private fun ColumnScope.Body(component: StaffListComponent, state: StaffListState, s: AppStrings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // weight(1f), so the table below is bounded and scrolls rather than being clipped
            // to whatever is left over.
            .weight(1f)
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // No AnfasScreenHeader here: the top bar already carries the title, and printing
        // "Staff" twice down the left edge of the same screen is how a pushed screen ends up
        // looking like two screens stacked.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.staff.subtitle,
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AnfasPrimaryButton(
                text = s.staff.addStaff,
                onClick = component::onAddStaff,
                icon = AnfasIcons.PersonAdd,
            )
        }

        state.notice?.let { notice ->
            NoticeRow(text = notice.render(s), onDismiss = component::onNoticeShown)
        }

        when (val content = state.content) {
            StaffListContent.Loading -> Unit

            is StaffListContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.staff.loadFailedTitle,
                message = content.message,
            )

            is StaffListContent.Loaded -> if (content.staff.isEmpty()) {
                AnfasEmptyState(
                    icon = AnfasIcons.Group,
                    title = s.staff.emptyTitle,
                    message = s.staff.emptyMessage,
                )
            } else {
                StaffTable(state = state, component = component, s = s)
            }
        }
    }
}

@Composable
private fun StaffTable(state: StaffListState, component: StaffListComponent, s: AppStrings) {
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(items = state.staff, key = { it.id }) { account ->
                StaffRow(
                    account = account,
                    isCurrentUser = account.id == state.currentUserId,
                    onToggleEnabled = {
                        component.onToggleEnabled(account.id, !account.isEnabled)
                    },
                    onResetPassword = {
                        component.onResetPassword(account.id, account.displayName)
                    },
                    s = s,
                )
                AnfasTableDivider()
            }
        }
        Text(
            text = s.staff.showingStaff(state.staff.size),
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun StaffRow(
    account: StaffAccount,
    isCurrentUser: Boolean,
    onToggleEnabled: () -> Unit,
    onResetPassword: () -> Unit,
    s: AppStrings,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = account.displayName,
                        style = AnfasTheme.textStyles.bodyLarge,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isCurrentUser) {
                        AnfasStatusChip(label = s.staff.you, tone = ChipTone.Neutral)
                    }
                    if (!account.isEnabled) {
                        AnfasStatusChip(label = s.staff.disabled, tone = ChipTone.Critical)
                    }
                }
                Text(
                    // Latin-only, so an LTR run keeps the "@"-free username intact in Arabic.
                    text = account.username,
                    style = AnfasTheme.textStyles.dataMonoLtr,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            account.roles.sortedBy { it.name }.forEach { role ->
                AnfasStatusChip(label = role.label(s), tone = ChipTone.Neutral)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextAction(text = s.staff.resetPassword, onClick = onResetPassword)
            // Self-disable is not offered at all rather than offered and refused. The repository
            // still enforces it — this only avoids putting a button there that cannot work.
            if (!isCurrentUser) {
                AnfasTextAction(
                    text = if (account.isEnabled) s.staff.disable else s.staff.enable,
                    onClick = onToggleEnabled,
                )
            }
        }
    }
}

@Composable
private fun AddStaffDialog(
    dialog: StaffDialog.Add,
    state: StaffListState,
    component: StaffListComponent,
    s: AppStrings,
) {
    AnfasDialog(
        title = s.staff.addStaff,
        onDismissRequest = component::onDismissDialog,
        closeContentDescription = s.common.close,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = dialog.displayName,
                onValueChange = { component.onAddFieldChanged(displayName = it) },
                label = s.auth.displayName,
                enabled = !state.isSubmitting,
                errorMessage = s.auth.problemDisplayNameBlank
                    .takeIf { CredentialProblem.DisplayNameBlank in dialog.problems },
                modifier = Modifier.fillMaxWidth(),
            )
            AnfasTextField(
                value = dialog.username,
                onValueChange = { component.onAddFieldChanged(username = it) },
                label = s.auth.username,
                enabled = !state.isSubmitting,
                errorMessage = when {
                    CredentialProblem.UsernameTaken in dialog.problems ->
                        s.auth.problemUsernameTaken

                    CredentialProblem.UsernameTooShort in dialog.problems ->
                        s.auth.problemUsernameTooShort

                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AnfasTextField(
                value = dialog.password,
                onValueChange = { component.onAddFieldChanged(password = it) },
                label = s.auth.password,
                enabled = !state.isSubmitting,
                isPassword = true,
                errorMessage = s.auth.problemPasswordTooShort
                    .takeIf { CredentialProblem.PasswordTooShort in dialog.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = s.staff.columnRoles,
                style = AnfasTheme.textStyles.labelCaps,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.assignableRoles.chunked(ROLES_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { role ->
                            AnfasChoiceChip(
                                label = role.label(s),
                                selected = role in dialog.roles,
                                onClick = { component.onRoleToggled(role) },
                            )
                        }
                    }
                }
            }

            DialogActions(
                confirmText = if (state.isSubmitting) s.staff.creating else s.staff.addStaff,
                enabled = !state.isSubmitting,
                onConfirm = component::onSubmitDialog,
                onCancel = component::onDismissDialog,
                s = s,
            )
        }
    }
}

@Composable
private fun ResetPasswordDialog(
    dialog: StaffDialog.ResetPassword,
    state: StaffListState,
    component: StaffListComponent,
    s: AppStrings,
) {
    AnfasDialog(
        title = s.staff.resetPasswordFor(dialog.displayName),
        onDismissRequest = component::onDismissDialog,
        closeContentDescription = s.common.close,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = dialog.password,
                onValueChange = component::onResetFieldChanged,
                label = s.staff.newPassword,
                enabled = !state.isSubmitting,
                isPassword = true,
                errorMessage = s.auth.problemPasswordTooShort
                    .takeIf { CredentialProblem.PasswordTooShort in dialog.problems },
                modifier = Modifier.fillMaxWidth(),
            )
            DialogActions(
                confirmText = if (state.isSubmitting) s.staff.saving else s.staff.save,
                enabled = !state.isSubmitting && dialog.password.isNotEmpty(),
                onConfirm = component::onSubmitDialog,
                onCancel = component::onDismissDialog,
                s = s,
            )
        }
    }
}

@Composable
private fun DialogActions(
    confirmText: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    s: AppStrings,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
    ) {
        AnfasSecondaryButton(text = s.common.cancel, onClick = onCancel)
        AnfasPrimaryButton(text = confirmText, onClick = onConfirm, enabled = enabled)
    }
}

@Composable
private fun NoticeRow(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AnfasTextAction(text = strings.common.dismiss, onClick = onDismiss)
    }
}

private fun StaffNotice.render(s: AppStrings): String = when (this) {
    is StaffNotice.Created -> s.staff.created(displayName)

    StaffNotice.PasswordReset -> s.staff.passwordReset

    is StaffNotice.EnabledChanged ->
        if (enabled) s.staff.accountEnabled else s.staff.accountDisabled

    StaffNotice.WouldLockOutDevice -> s.staff.wouldLockOutDevice

    StaffNotice.AccountGone -> s.staff.accountGone

    is StaffNotice.Failed -> message
}

/**
 * Role labels. Owner appears here even though it cannot be assigned, because the owner's own row
 * still has to render it.
 *
 * Public, and lives here rather than in `:core:i18n`, because i18n does not depend on
 * `:core:auth` and should not start: it owns the *strings*, and which Role each belongs to is
 * this feature's business. The app shell uses it to label whoever is signed in.
 */
fun Role.label(s: AppStrings): String = when (this) {
    Role.Owner -> s.staff.roleOwner
    Role.Admin -> s.staff.roleAdmin
    Role.Therapist -> s.staff.roleTherapist
    Role.Coach -> s.staff.roleCoach
    Role.Receptionist -> s.staff.roleReceptionist
    Role.Member -> s.staff.roleMember
}

private const val ROLES_PER_ROW = 2
