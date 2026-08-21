package com.anfas.feature.subscriptions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.anfas.core.common.RelativeTime
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.format
import com.anfas.core.i18n.strings
import com.anfas.core.model.FailureReason
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Domain -> presentation for the queue. Lives in the feature because :core:designsystem must not
 * know domain types and :core:model must not know the design system or language.
 */
internal val ReminderStatus.chipTone: ChipTone
    get() = when (this) {
        ReminderStatus.QUEUED -> ChipTone.Neutral
        ReminderStatus.SENT -> ChipTone.Positive
        ReminderStatus.FAILED -> ChipTone.Critical
    }

internal fun ReminderStatus.label(s: AppStrings): String = when (this) {
    ReminderStatus.QUEUED -> s.reminders.tabQueued
    ReminderStatus.SENT -> s.reminders.tabSent
    ReminderStatus.FAILED -> s.reminders.tabFailed
}

/**
 * The queue shows the raw template name deliberately — it is the identifier staff match against
 * Meta's console, so it is not translated.
 */
internal val ReminderTemplate.label: String get() = templateName

/** The queue's "Scheduled" column: no comma between day and time, per the export. */
@Composable
internal fun Reminder.scheduledLabel(): String {
    val s = strings
    val stamp = remember(scheduledAt) {
        RelativeTime.classify(scheduledAt, Clock.System.now(), TimeZone.currentSystemDefault())
    }
    return s.format(stamp, separator = " ")
}

internal fun FailureReason.title(s: AppStrings): String = when (this) {
    FailureReason.NOT_OPTED_IN -> s.reminders.failureNotOptedInTitle
    FailureReason.INVALID_PHONE_NUMBER -> s.reminders.failureInvalidPhoneTitle
    FailureReason.RATE_LIMITED -> s.reminders.failureRateLimitedTitle
    FailureReason.TEMPLATE_PAUSED -> s.reminders.failureTemplatePausedTitle
    FailureReason.UNKNOWN -> s.reminders.failureUnknownTitle
}

internal fun FailureReason.explanation(s: AppStrings): String = when (this) {
    FailureReason.NOT_OPTED_IN -> s.reminders.failureNotOptedInExplanation
    FailureReason.INVALID_PHONE_NUMBER -> s.reminders.failureInvalidPhoneExplanation
    FailureReason.RATE_LIMITED -> s.reminders.failureRateLimitedExplanation
    FailureReason.TEMPLATE_PAUSED -> s.reminders.failureTemplatePausedExplanation
    FailureReason.UNKNOWN -> s.reminders.failureUnknownExplanation
}

/**
 * The one-line reason under the status chip. Falls back to the reason's own title when the
 * provider gave no extra detail, so a failed row is never blank.
 */
internal fun Reminder.failureSummary(s: AppStrings): String? =
    failure?.let { it.detail ?: it.reason.title(s) }
