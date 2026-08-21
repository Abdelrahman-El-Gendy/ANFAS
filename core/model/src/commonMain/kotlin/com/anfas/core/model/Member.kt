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
