package com.anfas.core.model

import kotlin.time.Instant

/**
 * One outbound WhatsApp reminder.
 *
 * [memberName] and [phone] are denormalised onto the reminder on purpose: the queue is a
 * staff worklist that must stay readable and searchable without joining members, and it must
 * still show who a message was for after the member row changes.
 */
data class Reminder(
    val id: ReminderId,
    val memberId: MemberId,
    val memberName: String,
    val phone: String,
    val template: ReminderTemplate,
    val scheduledAt: Instant,
    val attempts: Int,
    val status: ReminderStatus,
    /** Non-null only when [status] is [ReminderStatus.FAILED]. */
    val failure: ReminderFailure?,
) {
    /**
     * Whether the Retry action is offered. Retrying a message Meta rejected for policy or a
     * number that is malformed would fail again and burn an attempt, so the design disables
     * it — the failed-detail screen shows an explanatory tooltip instead.
     */
    val canRetry: Boolean
        get() = status == ReminderStatus.FAILED && failure?.reason?.isRetryable == true
}

/**
 * The tabs on the queue screen. The two exported screens disagree — `whatsapp-reminder-queue`
 * shows Queued/Sent/Failed while `reminder-queue-empty` shows Pending/Sent/Failed/Archived.
 * This follows the former: it is the screen with the real table, and "Archived" has no data or
 * behaviour defined anywhere in the export.
 */
enum class ReminderStatus {
    QUEUED,
    SENT,
    FAILED,
}

/** Meta-approved message templates, named as they appear in the queue. */
enum class ReminderTemplate(val templateName: String, val language: TemplateLanguage) {
    REMINDER_AR("reminder_ar", TemplateLanguage.ARABIC),
    REMINDER_EN("reminder_en", TemplateLanguage.ENGLISH),
    PAYMENT_DUE("payment_due", TemplateLanguage.ENGLISH),
    MARKETING_PROMO("marketing_promo", TemplateLanguage.ENGLISH),
}

enum class TemplateLanguage {
    ARABIC,
    ENGLISH,
}

/**
 * Why a send failed, plus the provider's code for support conversations.
 *
 * [detail] is the provider's raw text when there is one; the human explanation comes from
 * [reason] so it stays consistent regardless of what the API returned.
 */
data class ReminderFailure(
    val reason: FailureReason,
    /** e.g. 131047. Null when the provider gave none. */
    val providerCode: Int?,
    val lastAttemptAt: Instant?,
    val detail: String? = null,
)

/**
 * Every failure the export shows, with whether retrying can possibly help.
 *
 * Title and explanation live in AppStrings, not here: they are UI copy and must be translatable.
 * Storage is unaffected — persistence writes .name, verified in ReminderMappers.
 *
 * [RATE_LIMITED] is the only retryable one: it is transient and the queue is already backing
 * off. The rest need a human to change something — an opt-in, a phone number, or a template
 * approval — so retrying would waste an attempt.
 */
enum class FailureReason(val title: String, val explanation: String, val isRetryable: Boolean) {
    NOT_OPTED_IN(
        title = "Recipient has not opted in",
        explanation = "Meta requires members to opt in before receiving template messages. " +
            "Ask them to send any message to the gym's WhatsApp number first.",
        isRetryable = false,
    ),
    INVALID_PHONE_NUMBER(
        title = "Invalid phone number",
        explanation = "The number could not be reached. Correct it on the member's profile " +
            "and the next scheduled run will pick it up.",
        isRetryable = false,
    ),
    RATE_LIMITED(
        title = "Rate limited",
        explanation = "The provider is throttling sends. The queue retries automatically; " +
            "no action is needed.",
        isRetryable = true,
    ),
    TEMPLATE_PAUSED(
        title = "Template paused by Meta",
        explanation = "This template is paused and cannot be sent. Choose another template " +
            "or wait for Meta to reinstate it.",
        isRetryable = false,
    ),
    UNKNOWN(
        title = "Message not delivered",
        explanation = "The provider did not say why. Check the technical details below.",
        isRetryable = true,
    ),
}
