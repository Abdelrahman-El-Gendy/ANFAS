package com.anfas.feature.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasBulkActionBar
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasCheckbox
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDetailTopBar
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSearchField
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTableFooter
import com.anfas.core.designsystem.AnfasTableHeaderCell
import com.anfas.core.designsystem.AnfasTableHeaderRow
import com.anfas.core.designsystem.AnfasTableRow
import com.anfas.core.designsystem.AnfasTabs
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.AnfasTriStateCheckbox
import com.anfas.core.designsystem.EmptyStateAction
import com.anfas.core.designsystem.Tab
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.designsystem.Tone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.strings
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate

/**
 * The reminder queue.
 *
 * Departures from the export, all for the same reason — the send job does not exist yet, so
 * these controls would be dead:
 *  - no "Send test message" or "Run Queue" button;
 *  - no "Daily job last ran 06:00 today" line, which has no source of truth.
 *
 * Also: the template filter is a chip row rather than a `<select>`, because there is no select
 * component in the design system yet and five chips read fine at both form factors.
 */
@Composable
fun ReminderQueueScreen(component: ReminderQueueComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // No longer a bottom-bar destination: opened from the dashboard's renewal tile on a
            // phone and from the rail on desktop, so it owns a back affordance. The title moves
            // into it rather than being printed twice.
            AnfasDetailTopBar(
                title = s.reminders.title,
                onBack = component::onClose,
                backContentDescription = s.common.back,
                // Hidden rather than disabled for a role that may not change the queue, matching
                // how every other permission-gated action in the app behaves.
                actions = if (state.mayBuildQueue) {
                    {
                        AnfasTextAction(
                            text = if (state.isBuilding) {
                                s.reminders.building
                            } else {
                                s.reminders.buildQueue
                            },
                            onClick = component::onBuildQueue,
                            enabled = !state.isBuilding,
                        )
                    }
                } else {
                    null
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = AnfasTheme.spacing.marginMobile)
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = s.reminders.subtitle,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                AnfasTabs(
                    tabs = StatusTabs.map { status ->
                        Tab(
                            label = status.label(s),
                            count = state.counts[status],
                            emphasiseCount = status == ReminderStatus.FAILED &&
                                state.counts.failed > 0,
                        )
                    },
                    selectedIndex = StatusTabs.indexOf(state.selectedStatus).coerceAtLeast(0),
                    onTabSelected = { component.onStatusSelected(StatusTabs[it]) },
                )

                AnfasSearchField(
                    value = state.query,
                    onValueChange = component::onQueryChanged,
                    placeholder = s.reminders.searchPlaceholder,
                    clearContentDescription = s.common.clearSearch,
                    modifier = Modifier.fillMaxWidth(),
                )

                TemplateFilterRow(
                    selected = state.templateFilter,
                    onSelected = component::onTemplateFilterChanged,
                )

                state.notice?.let { notice ->
                    NoticeBar(text = notice.render(s), onDismiss = component::onNoticeShown)
                }

                when (val content = state.content) {
                    ReminderQueueContent.Loading -> Box(Modifier.fillMaxSize())

                    is ReminderQueueContent.Failed -> AnfasEmptyState(
                        icon = AnfasIcons.ErrorOutline,
                        title = s.reminders.loadFailedTitle,
                        message = content.message,
                    )

                    is ReminderQueueContent.Empty -> QueueEmptyState(
                        content = content,
                        sentCount = state.counts.sent,
                        onClearFilters = {
                            component.onQueryChanged("")
                            component.onTemplateFilterChanged(null)
                        },
                    )

                    is ReminderQueueContent.Loaded -> ReminderTable(
                        reminders = content.reminders,
                        state = state,
                        component = component,
                    )
                }
            }
        }

        if (state.supportsSelection && state.selectedIds.isNotEmpty()) {
            AnfasBulkActionBar(
                selectedCount = state.selectedIds.size,
                label = s.common.selected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            ) {
                AnfasTextAction(
                    text = s.common.dismiss,
                    onClick = component::onClearSelection,
                    emphasis = TextActionEmphasis.Muted,
                )
                AnfasPrimaryButton(
                    text = s.reminders.retrySelected(state.selectedIds.size),
                    onClick = component::onRetrySelected,
                )
            }
        }
    }

    state.openedFailure?.let { reminder ->
        FailedReminderDialog(
            reminder = reminder,
            onDismiss = component::onDismissFailure,
            onRetry = { component.onRetry(reminder.id) },
            onOpenMember = { component.onOpenMember(reminder.memberId) },
        )
    }
}

@Composable
private fun TemplateFilterRow(
    selected: ReminderTemplate?,
    onSelected: (ReminderTemplate?) -> Unit,
) {
    val s = strings
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnfasChoiceChip(
            label = s.reminders.allTemplates,
            selected = selected == null,
            onClick = { onSelected(null) },
        )
        ReminderTemplate.entries.forEach { template ->
            AnfasChoiceChip(
                label = template.label,
                selected = selected == template,
                onClick = { onSelected(template) },
            )
        }
    }
}

@Composable
private fun QueueEmptyState(
    content: ReminderQueueContent.Empty,
    sentCount: Int,
    onClearFilters: () -> Unit,
) {
    val s = strings
    // A filtered empty tab is a different situation from a genuinely clear queue: one is
    // "your filters match nothing", the other is "the system is healthy".
    if (content.isFiltered) {
        AnfasEmptyState(
            icon = AnfasIcons.FilterList,
            title = s.reminders.filteredEmptyTitle,
            message = s.reminders.filteredEmptyMessage,
            primaryAction = EmptyStateAction(
                label = s.reminders.clearFilters,
                onClick = onClearFilters,
                icon = AnfasIcons.Close,
            ),
        )
        return
    }
    when (content.status) {
        ReminderStatus.FAILED -> AnfasEmptyState(
            icon = AnfasIcons.CheckCircle,
            title = s.reminders.noFailedTitle,
            message = if (sentCount > 0) {
                s.reminders.noFailedMessageWithCount(sentCount)
            } else {
                s.reminders.noFailedMessage
            },
            tone = Tone.Informational,
        )

        ReminderStatus.QUEUED -> AnfasEmptyState(
            icon = AnfasIcons.Schedule,
            title = s.reminders.nothingQueuedTitle,
            message = s.reminders.nothingQueuedMessage,
        )

        ReminderStatus.SENT -> AnfasEmptyState(
            icon = AnfasIcons.Send,
            title = s.reminders.nothingSentTitle,
            message = s.reminders.nothingSentMessage,
        )
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    val s = strings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                com.anfas.core.designsystem.AnfasShapes.base,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AnfasTextAction(
            text = s.common.dismiss,
            onClick = onDismiss,
            emphasis = TextActionEmphasis.Muted,
        )
    }
}

@Composable
private fun ReminderTable(
    reminders: List<Reminder>,
    state: ReminderQueueState,
    component: ReminderQueueComponent,
) {
    val s = strings
    val allVisibleSelected = reminders.isNotEmpty() &&
        state.selectedIds.containsAll(reminders.map { it.id })

    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        AnfasTableHeaderRow {
            if (state.supportsSelection) {
                Box(Modifier.width(SelectionColumnWidth)) {
                    AnfasTriStateCheckbox(
                        state = when {
                            allVisibleSelected -> ToggleableState.On
                            state.selectedIds.isEmpty() -> ToggleableState.Off
                            else -> ToggleableState.Indeterminate
                        },
                        onClick = component::onToggleSelectAll,
                    )
                }
            }
            AnfasTableHeaderCell(s.reminders.columnMember, Modifier.weight(WEIGHT_MEMBER))
            AnfasTableHeaderCell(s.reminders.columnPhone, Modifier.weight(WEIGHT_PHONE))
            AnfasTableHeaderCell(s.reminders.columnTemplate, Modifier.weight(WEIGHT_TEMPLATE))
            AnfasTableHeaderCell(s.reminders.columnScheduled, Modifier.weight(WEIGHT_SCHEDULED))
            AnfasTableHeaderCell(s.reminders.columnStatus, Modifier.weight(WEIGHT_STATUS))
            AnfasTableHeaderCell(
                text = s.reminders.columnActions,
                modifier = Modifier.width(ActionsColumnWidth),
                textAlign = TextAlign.End,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(items = reminders, key = { it.id.value }) { reminder ->
                ReminderRow(
                    reminder = reminder,
                    selected = reminder.id in state.selectedIds,
                    selectable = state.supportsSelection,
                    isLast = reminder == reminders.last(),
                    onToggle = { component.onToggleSelected(reminder.id) },
                    onOpen = { component.onOpenFailure(reminder.id) },
                    onRetry = { component.onRetry(reminder.id) },
                    onOpenMember = { component.onOpenMember(reminder.memberId) },
                )
            }
        }
        AnfasTableFooter {
            Text(
                text = s.reminders.showingMessages(
                    count = reminders.size,
                    status = state.selectedStatus.label(s).lowercase(),
                ),
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    selected: Boolean,
    selectable: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onOpenMember: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val s = strings
    Row(modifier = Modifier.fillMaxWidth()) {
        // The export marks failed rows with a 4px error stripe on the leading edge.
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    if (reminder.status == ReminderStatus.FAILED) {
                        scheme.error
                    } else {
                        Color.Transparent
                    },
                ),
        )
        Column(Modifier.weight(1f)) {
            AnfasTableRow(
                onClick = if (reminder.status == ReminderStatus.FAILED) onOpen else null,
                showDivider = !isLast,
            ) {
                if (selectable) {
                    Box(Modifier.width(SelectionColumnWidth)) {
                        AnfasCheckbox(checked = selected, onCheckedChange = { onToggle() })
                    }
                }
                Text(
                    text = reminder.memberName,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(WEIGHT_MEMBER),
                )
                Text(
                    text = reminder.phone,
                    style = AnfasTheme.textStyles.dataMonoLtr,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(WEIGHT_PHONE),
                )
                Text(
                    text = reminder.template.label,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(WEIGHT_TEMPLATE),
                )
                Text(
                    text = reminder.scheduledLabel(),
                    style = AnfasTheme.textStyles.dataMonoLtr,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(WEIGHT_SCHEDULED),
                )
                Column(
                    modifier = Modifier.weight(WEIGHT_STATUS),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AnfasStatusChip(
                        label = reminder.status.label(s),
                        tone = reminder.status.chipTone,
                    )
                    reminder.failureSummary(s)?.let { summary ->
                        Text(
                            text = summary,
                            style = AnfasTheme.textStyles.labelCaps,
                            color = scheme.error.copy(alpha = 0.80f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.width(ActionsColumnWidth),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (reminder.canRetry) {
                        AnfasTextAction(text = s.common.retry, onClick = onRetry)
                    } else {
                        AnfasTextAction(
                            text = s.common.open,
                            onClick = onOpenMember,
                            emphasis = TextActionEmphasis.Muted,
                        )
                    }
                }
            }
        }
    }
    if (!isLast) AnfasTableDivider()
}

private val StatusTabs = listOf(
    ReminderStatus.QUEUED,
    ReminderStatus.SENT,
    ReminderStatus.FAILED,
)

private const val WEIGHT_MEMBER = 2f
private const val WEIGHT_PHONE = 2f
private const val WEIGHT_TEMPLATE = 1.6f
private const val WEIGHT_SCHEDULED = 1.4f
private const val WEIGHT_STATUS = 2f
private val SelectionColumnWidth = 40.dp
private val ActionsColumnWidth = 96.dp

/** Typed notice -> sentence. The component deliberately does not do this itself. */
private fun QueueNotice.render(s: AppStrings): String = when (this) {
    is QueueNotice.Requeued ->
        if (requeued == requested) {
            s.reminders.requeuedAll(requeued)
        } else {
            s.reminders.requeuedPartial(requeued, requested)
        }

    QueueNotice.NothingRetryable -> s.reminders.requeuedNone

    is QueueNotice.QueueBuilt -> s.reminders.queueBuilt(queued)

    is QueueNotice.QueueBuiltNothing ->
        s.reminders.queueBuiltNothing(noConsent, noPhone, alreadyQueued)

    is QueueNotice.Failed -> message
}
