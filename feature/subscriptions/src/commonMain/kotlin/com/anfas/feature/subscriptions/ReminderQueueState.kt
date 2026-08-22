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
 *
 * It is a **typed** value, not a formatted sentence. A Decompose component cannot read Compose
 * state, so building prose here would either hardcode English or need a string lookup that
 * ignores the language toggle. The UI renders it. Tests assert on the type, which is also a
 * better test than comparing English.
 */
data class ReminderQueueState(
    val selectedStatus: ReminderStatus = ReminderStatus.FAILED,
    val counts: ReminderCounts = ReminderCounts(),
    val query: String = "",
    val templateFilter: ReminderTemplate? = null,
    val content: ReminderQueueContent = ReminderQueueContent.Loading,
    val selectedIds: Set<ReminderId> = emptySet(),
    val openedFailure: Reminder? = null,
    val notice: QueueNotice? = null,
    /** Whether this session holds `Permission.RETRY_REMINDERS`. */
    val mayRetry: Boolean = false,
) {
    val visibleReminders: List<Reminder>
        get() = (content as? ReminderQueueContent.Loaded)?.reminders ?: emptyList()

    /**
     * Bulk actions only make sense where a retry is possible — the Failed tab — and only for a
     * session that may retry.
     *
     * Viewing the queue and re-sending from it are separate permissions: a retry sends a WhatsApp
     * message on the gym's account, which is not the same act as reading who failed. The route
     * guard cannot catch this, because both live on this one screen.
     */
    val supportsSelection: Boolean
        get() = selectedStatus == ReminderStatus.FAILED && mayRetry

    val isFiltered: Boolean get() = query.isNotBlank() || templateFilter != null
}

/** The result of an action, as structure. Rendered by the screen. */
sealed interface QueueNotice {
    /** [requeued] of [requested] actually went; the rest could not be retried. */
    data class Requeued(val requeued: Int, val requested: Int) : QueueNotice
    data object NothingRetryable : QueueNotice
    data class Failed(val message: String) : QueueNotice
}
