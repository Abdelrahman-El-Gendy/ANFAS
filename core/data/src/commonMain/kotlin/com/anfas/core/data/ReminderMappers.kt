package com.anfas.core.data

import com.anfas.core.database.ReminderEntity
import com.anfas.core.model.FailureReason
import com.anfas.core.model.MemberId
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderFailure
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import kotlin.time.Instant

/**
 * Storage <-> domain.
 *
 * Unrecognised enum strings degrade rather than throw, for the same reason as members: one
 * unreadable row must not empty the whole queue. An unknown status becomes FAILED with
 * [FailureReason.UNKNOWN] — visible and actionable — rather than QUEUED, which would silently
 * schedule a send nobody asked for.
 */
internal fun ReminderEntity.toDomain(): Reminder {
    val resolvedStatus = ReminderStatus.entries.firstOrNull { it.name == status }
        ?: ReminderStatus.FAILED
    val resolvedFailure = when {
        failureReason != null || resolvedStatus == ReminderStatus.FAILED -> ReminderFailure(
            reason = FailureReason.entries.firstOrNull { it.name == failureReason }
                ?: FailureReason.UNKNOWN,
            providerCode = failureProviderCode,
            lastAttemptAt = failureLastAttemptEpochMs?.let(Instant::fromEpochMilliseconds),
            detail = failureDetail,
        )

        else -> null
    }
    return Reminder(
        id = ReminderId(id),
        memberId = MemberId(memberId),
        memberName = memberName,
        phone = phone,
        template = ReminderTemplate.entries.firstOrNull { it.name == template }
            ?: ReminderTemplate.PAYMENT_DUE,
        scheduledAt = Instant.fromEpochMilliseconds(scheduledAtEpochMs),
        attempts = attempts,
        status = resolvedStatus,
        failure = resolvedFailure.takeIf { resolvedStatus == ReminderStatus.FAILED },
    )
}

internal fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id.value,
    memberId = memberId.value,
    memberName = memberName,
    phone = phone,
    template = template.name,
    scheduledAtEpochMs = scheduledAt.toEpochMilliseconds(),
    attempts = attempts,
    status = status.name,
    failureReason = failure?.reason?.name,
    failureProviderCode = failure?.providerCode,
    failureLastAttemptEpochMs = failure?.lastAttemptAt?.toEpochMilliseconds(),
    failureDetail = failure?.detail,
)
