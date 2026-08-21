package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.database.ReminderDao
import com.anfas.core.database.ReminderStatusCount
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Room-backed reminder queue. The daily send job is not implemented here — when it lands it
 * writes through [upsert] and no screen changes.
 */
internal class OfflineFirstReminderRepository(private val dao: ReminderDao) : ReminderRepository {

    override fun observeQueue(
        status: ReminderStatus,
        query: String,
        template: ReminderTemplate?,
    ): Flow<AppResult<List<Reminder>>> = dao.observeByStatus(
        status = status.name,
        query = query.trim(),
        template = template?.name,
    ).asAppResult("Could not load the reminder queue") { rows -> rows.map { it.toDomain() } }

    override fun observeCounts(): Flow<AppResult<ReminderCounts>> =
        dao.observeStatusCounts().asAppResult("Could not count reminders") { it.toCounts() }

    override fun observeReminder(id: ReminderId): Flow<AppResult<Reminder?>> =
        dao.observeById(id.value)
            .asAppResult("Could not load reminder ${id.value}") { it?.toDomain() }

    override suspend fun retry(ids: List<ReminderId>): AppResult<Int> =
        runStorage("Could not retry reminders") {
            if (ids.isEmpty()) return@runStorage 0
            // Re-read current state rather than trusting the caller's snapshot: the row may
            // have been requeued or resolved since the screen rendered it.
            val retryable = ids.filter { id ->
                dao.observeById(id.value).first()?.toDomain()?.canRetry == true
            }
            if (retryable.isEmpty()) 0 else dao.requeue(retryable.map { it.value })
        }

    override suspend fun upsert(reminders: List<Reminder>): AppResult<Unit> =
        runStorage("Could not save reminders") { dao.upsertAll(reminders.map { it.toEntity() }) }
}

private fun List<ReminderStatusCount>.toCounts(): ReminderCounts {
    val byStatus = associate { it.status to it.count }
    return ReminderCounts(
        queued = byStatus[ReminderStatus.QUEUED.name] ?: 0,
        sent = byStatus[ReminderStatus.SENT.name] ?: 0,
        failed = byStatus[ReminderStatus.FAILED.name] ?: 0,
    )
}
