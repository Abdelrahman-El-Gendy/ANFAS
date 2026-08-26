package com.anfas.core.model

import kotlin.time.Instant

/**
 * A gym member.
 *
 * [membershipNumber] is the human-facing identifier staff read out and search by (rendered as
 * `ID: #88392`); [id] is the storage key. They are deliberately separate — a member can be
 * re-issued a number without becoming a different row.
 *
 * [phone] is nullable because a walk-in can be registered without one, but a member with no
 * phone cannot receive WhatsApp reminders — and it is what OCR intake matches against to catch
 * someone signing up twice on paper.
 */
data class Member(
    val id: MemberId,
    val fullName: String,
    val membershipNumber: String,
    val phone: String?,
    val status: MembershipStatus,
    val lastCheckInAt: Instant?,
    val avatarUrl: String?,
    /**
     * Whether this member has agreed to receive WhatsApp template messages.
     *
     * **Defaults to false and must stay that way.** Meta requires opt-in before a template message
     * may be sent, there is no API to ask whether someone has opted in, and consent is not
     * something a migration can infer — so the only honest starting value for every existing row
     * is "we have not asked". The consequence is deliberate: the reminder queue builds empty until
     * staff actually mark members, and `ReminderScheduler` reports how many it skipped for this
     * reason rather than looking broken.
     */
    val whatsappOptIn: Boolean = false,
    /**
     * Which language this member reads, deciding `reminder_ar` against `reminder_en`.
     *
     * Null means "not asked", and the scheduler falls back to Arabic. Deliberately *not* the app's
     * own UI language: that is a device setting belonging to whichever receptionist is on shift,
     * and it says nothing about what the member reads.
     */
    val preferredLanguage: TemplateLanguage? = null,
) {
    /** Initials for the avatar fallback — the design shows "DT", "EL" when there is no photo. */
    val initials: String
        get() = fullName.trim().split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
}

/**
 * Every state the design actually renders, and no more. Sourced from the Stitch export:
 * `member-directory` shows Active and Expired, `sync-conflict` shows Suspended and
 * `offline-banner` shows Paused. "Trial" appears in `ocr-intake-review` but is a *plan*, not a
 * status — don't add it here.
 */
enum class MembershipStatus {
    ACTIVE,
    EXPIRED,
    SUSPENDED,
    PAUSED,
    ;

    /** True when the member may enter the gym today. */
    val grantsAccess: Boolean get() = this == ACTIVE
}
