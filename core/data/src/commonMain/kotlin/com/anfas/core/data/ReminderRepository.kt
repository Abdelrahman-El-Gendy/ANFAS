package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import kotlinx.coroutines.flow.Flow

/** How many reminders sit in each tab — drives the queue's badge counts. */
data class ReminderCounts(
    val queued: Int = 0,
    val sent: Int = 0,
    val failed: Int = 0,
) {
    operator fun get(status: ReminderStatus): Int = when (status) {
        ReminderStatus.QUEUED -> queued
        ReminderStatus.SENT -> sent
        ReminderStatus.FAILED -> failed
    }
}

interface ReminderRepository {

    /** One tab's worth of the queue, optionally text- and template-filtered. */
    fun observeQueue(
        status: ReminderStatus,
        query: String = "",
        template: ReminderTemplate? = null,
    ): Flow<AppResult<List<Reminder>>>

    fun observeCounts(): Flow<AppResult<ReminderCounts>>

    fun observeReminder(id: ReminderId): Flow<AppResult<Reminder?>>

    /**
     * Requeues the given reminders, **skipping any that cannot be retried**, and reports how
     * many were actually requeued.
     *
     * The filtering is here rather than at the call site because it is a rule about the data,
     * not about one screen: a bulk selection can legitimately mix a rate-limited message with
     * one Meta rejected, and retrying the latter would burn an attempt and fail again.
     */
    suspend fun retry(ids: List<ReminderId>): AppResult<Int>

    suspend fun upsert(reminders: List<Reminder>): AppResult<Unit>
}
