package com.anfas.feature.members

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIconButton
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasSearchField
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableFooter
import com.anfas.core.designsystem.AnfasTableHeaderCell
import com.anfas.core.designsystem.AnfasTableHeaderRow
import com.anfas.core.designsystem.AnfasTableRow
import com.anfas.core.designsystem.AnfasTableScroll
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.EmptyStateAction
import com.anfas.core.designsystem.Tone
import com.anfas.core.i18n.strings
import com.anfas.core.model.Member
import com.anfas.core.model.MembershipStatus

/**
 * The members directory.
 *
 * Two deliberate departures from the export, both noted in design/stitch/TOKENS.md territory:
 *  - the table uses weighted columns instead of the export's `min-w-[600px]` +
 *    `overflow-x-auto`. Nesting a horizontal scroll around a lazy vertical list is fragile,
 *    and four columns fit the design's own 780px mobile viewport.
 *  - the footer reports the loaded count only. The export draws "Showing 1 to 4 of 128" with
 *    pager arrows; paging is not implemented, and non-functional arrows would be worse than
 *    none.
 */
@Composable
fun MembersListScreen(component: MembersListComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AnfasScreenHeader(
            title = s.members.title,
            subtitle = s.members.subtitle,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnfasSearchField(
                value = state.query,
                onValueChange = component::onQueryChanged,
                onClear = component::onClearSearch,
                placeholder = s.members.searchPlaceholder,
                clearContentDescription = s.common.clearSearch,
                modifier = Modifier.weight(1f),
            )
            AnfasPrimaryButton(
                text = s.members.addMember,
                icon = AnfasIcons.PersonAdd,
                onClick = component::onAddMember,
            )
        }

        when (val content = state.content) {
            MembersListContent.Loading -> Box(Modifier.fillMaxSize())

            is MembersListContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.Close,
                title = s.members.loadFailedTitle,
                message = content.message,
                tone = Tone.Informational,
            )

            MembersListContent.DirectoryEmpty -> AnfasEmptyState(
                icon = AnfasIcons.PersonAdd,
                title = s.members.emptyTitle,
                message = s.members.emptyMessage,
                tone = Tone.Invitation,
                secondaryAction = EmptyStateAction(
                    label = s.members.addMember,
                    onClick = component::onAddMember,
                    icon = AnfasIcons.PersonAdd,
                ),
                primaryAction = EmptyStateAction(
                    label = s.members.scanSheet,
                    onClick = component::onScanSheet,
                ),
            )

            is MembersListContent.NoMatches -> AnfasEmptyState(
                icon = AnfasIcons.PersonSearch,
                title = s.members.noMatchesTitle(content.query),
                message = s.members.noMatchesMessage,
                tone = Tone.Informational,
                primaryAction = EmptyStateAction(
                    label = s.members.clearSearchAction,
                    onClick = component::onClearSearch,
                    icon = AnfasIcons.ArrowBack,
                ),
            )

            is MembersListContent.Loaded -> MembersTable(
                members = content.members,
                onMemberClicked = component::onMemberSelected,
            )
        }
    }
}

@Composable
private fun MembersTable(
    members: List<Member>,
    onMemberClicked: (com.anfas.core.model.MemberId) -> Unit,
) {
    val s = strings
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        AnfasTableScroll {
            AnfasTableHeaderRow {
                AnfasTableHeaderCell(s.members.columnMember, Modifier.weight(COLUMN_WEIGHT_MEMBER))
                AnfasTableHeaderCell(s.members.columnStatus, Modifier.weight(COLUMN_WEIGHT_STATUS))
                AnfasTableHeaderCell(
                    s.members.columnLastCheckIn,
                    Modifier.weight(COLUMN_WEIGHT_CHECK_IN),
                )
                AnfasTableHeaderCell(
                    text = s.members.columnActions,
                    modifier = Modifier.width(ActionsColumnWidth),
                    textAlign = TextAlign.End,
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(items = members, key = { it.id.value }) { member ->
                    MemberRow(
                        member = member,
                        isLast = member == members.last(),
                        onClick = { onMemberClicked(member.id) },
                    )
                }
            }
        }
        AnfasTableFooter {
            Text(
                text = if (members.size ==
                    1
                ) {
                    "Showing 1 member"
                } else {
                    "Showing ${members.size} members"
                },
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemberRow(member: Member, isLast: Boolean, onClick: () -> Unit) {
    val s = strings
    // The export dims expired members' names to 70% rather than recolouring them — the status
    // chip is what carries the meaning.
    val nameAlpha = if (member.status == MembershipStatus.EXPIRED) 0.70f else 1f

    AnfasTableRow(onClick = onClick, showDivider = !isLast) {
        Row(
            modifier = Modifier.weight(COLUMN_WEIGHT_MEMBER),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MemberAvatar(member)
            Column {
                Text(
                    text = member.fullName,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = nameAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = s.members.idPrefix(member.membershipNumber),
                    style = AnfasTheme.textStyles.dataMonoLtr,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.weight(COLUMN_WEIGHT_STATUS)) {
            AnfasStatusChip(label = member.status.label(), tone = member.status.chipTone)
        }
        Text(
            text = member.lastCheckInLabel(),
            style = AnfasTheme.textStyles.dataMonoLtr,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(COLUMN_WEIGHT_CHECK_IN),
        )
        Box(Modifier.width(ActionsColumnWidth), contentAlignment = Alignment.CenterEnd) {
            AnfasIconButton(
                icon = AnfasIcons.MoreVert,
                contentDescription = s.members.actionsFor(member.fullName),
                onClick = onClick,
            )
        }
    }
}

private const val COLUMN_WEIGHT_MEMBER = 3f
private const val COLUMN_WEIGHT_STATUS = 1.3f
private const val COLUMN_WEIGHT_CHECK_IN = 1.6f
private val ActionsColumnWidth = 40.dp
