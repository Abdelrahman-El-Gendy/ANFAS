package com.anfas.feature.subscriptions

import com.anfas.core.common.RelativeTime
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Domain -> presentation for the queue. Lives in the feature because :core:designsystem must
 * not know domain types and :core:model must not know the design system.
 */
internal val ReminderStatus.label: String
    get() = when (this) {
        ReminderStatus.QUEUED -> "Queued"
        ReminderStatus.SENT -> "Sent"
        ReminderStatus.FAILED -> "Failed"
    }

internal val ReminderStatus.chipTone: ChipTone
    get() = when (this) {
        ReminderStatus.QUEUED -> ChipTone.Neutral
        ReminderStatus.SENT -> ChipTone.Positive
        ReminderStatus.FAILED -> ChipTone.Critical
    }

/** The queue shows the raw template name — it is what staff match against Meta's console. */
internal val ReminderTemplate.label: String get() = templateName

/** The queue's "Scheduled" column: no comma between day and time, per the export. */
internal fun Reminder.scheduledLabel(
    now: Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = RelativeTime.format(
    instant = scheduledAt,
    now = now,
    zone = zone,
    separator = " ",
)

/**
 * The one-line reason under the status chip. Falls back to the reason's own title when the
 * provider gave no extra detail, so the cell is never blank on a failed row.
 */
internal val Reminder.failureSummary: String?
    get() = failure?.let { it.detail ?: it.reason.title }
