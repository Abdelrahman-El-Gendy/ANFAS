package com.anfas.feature.subscriptions

import com.anfas.core.data.ReminderCounts
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate

/**
 * What one tab of the reminder queue is showing.
 *
 * [Empty] carries its status because the message is tab-specific — the export's empty Failed
 * tab reads "No failed reminders / System health is optimal", which would be nonsense on the
 * Queued tab.
 */
sealed interface ReminderQueueContent {
    data object Loading : ReminderQueueContent
    data class Loaded(val reminders: List<Reminder>) : ReminderQueueContent
    data class Empty(val status: ReminderStatus, val isFiltered: Boolean) : ReminderQueueContent
    data class Failed(val message: String) : ReminderQueueContent
}

/**
 * [selectedIds] is kept as ids rather than [Reminder]s so a list refresh cannot leave stale
 * copies selected; ids that vanish are simply dropped when the action runs.
 *
 * [notice] is transient feedback for an action whose result is otherwise invisible — retrying
 * a selection where nothing was actually retryable produces no list change at all, and silence
 * would read as a broken button.
 */
data class ReminderQueueState(
    val selectedStatus: ReminderStatus = ReminderStatus.FAILED,
    val counts: ReminderCounts = ReminderCounts(),
    val query: String = "",
    val templateFilter: ReminderTemplate? = null,
    val content: ReminderQueueContent = ReminderQueueContent.Loading,
    val selectedIds: Set<ReminderId> = emptySet(),
    val openedFailure: Reminder? = null,
    val notice: String? = null,
) {
    val visibleReminders: List<Reminder>
        get() = (content as? ReminderQueueContent.Loaded)?.reminders ?: emptyList()

    /** Bulk actions only make sense where a retry is possible, i.e. the Failed tab. */
    val supportsSelection: Boolean get() = selectedStatus == ReminderStatus.FAILED

    val isFiltered: Boolean get() = query.isNotBlank() || templateFilter != null
}
