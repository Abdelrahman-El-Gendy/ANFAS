package com.anfas.core.data

import com.anfas.core.database.ReminderDao
import com.anfas.core.database.ReminderEntity
import com.anfas.core.database.ReminderStatusCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory ReminderDao whose filtering and requeue semantics mirror the real SQL, so a
 * repository test cannot pass for the wrong reason.
 */
internal class FakeReminderDao(
    initial: List<ReminderEntity> = emptyList(),
) : ReminderDao {

    private val rows = MutableStateFlow(initial)

    var failure: Throwable? = null

    val current: List<ReminderEntity> get() = rows.value

    override fun observeByStatus(
        status: String,
        query: String,
        template: String?,
    ): Flow<List<ReminderEntity>> = rows.map { list ->
        failure?.let { throw it }
        list.filter { row ->
            row.status == status &&
                (query.isEmpty() ||
                    row.memberName.contains(query, ignoreCase = true) ||
                    row.phone.contains(query)) &&
                (template == null || row.template == template)
        }.sortedByDescending { it.scheduledAtEpochMs }
    }

    override fun observeStatusCounts(): Flow<List<ReminderStatusCount>> = rows.map { list ->
        failure?.let { throw it }
        list.groupingBy { it.status }.eachCount()
            .map { (status, count) -> ReminderStatusCount(status, count) }
    }

    override fun observeById(id: String): Flow<ReminderEntity?> = rows.map { list ->
        failure?.let { throw it }
        list.firstOrNull { it.id == id }
    }

    override suspend fun upsertAll(reminders: List<ReminderEntity>) {
        failure?.let { throw it }
        rows.value = (rows.value.associateBy { it.id } + reminders.associateBy { it.id })
            .values.toList()
    }

    override suspend fun requeue(ids: List<String>): Int {
        failure?.let { throw it }
        var touched = 0
        rows.value = rows.value.map { row ->
            if (row.id in ids) {
                touched++
                row.copy(
                    status = "QUEUED",
                    attempts = row.attempts + 1,
                    failureReason = null,
                    failureProviderCode = null,
                    failureLastAttemptEpochMs = null,
                    failureDetail = null,
                )
            } else {
                row
            }
        }
        return touched
    }
}

internal fun reminderEntity(
    id: String,
    name: String = "Omar Khaled",
    phone: String = "+20 100 123 4567",
    template: String = "REMINDER_AR",
    status: String = "FAILED",
    attempts: Int = 1,
    failureReason: String? = "RATE_LIMITED",
    scheduledAtEpochMs: Long = 1_700_000_000_000,
) = ReminderEntity(
    id = id,
    memberId = "m-$id",
    memberName = name,
    phone = phone,
    template = template,
    scheduledAtEpochMs = scheduledAtEpochMs,
    attempts = attempts,
    status = status,
    failureReason = failureReason,
    failureProviderCode = 131047,
    failureLastAttemptEpochMs = scheduledAtEpochMs,
    failureDetail = null,
)
