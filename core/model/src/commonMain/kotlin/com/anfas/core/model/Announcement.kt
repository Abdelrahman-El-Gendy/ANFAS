package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * A staff-authored gym-wide bulletin — the export's `create-announcement`.
 *
 * **This creates and tracks bulletins; it does not deliver anything to anyone.** The export's
 * own screen offers three delivery channels — In-app Notice, Push Notification, WhatsApp
 * Broadcast — and all three assume a member-facing surface this app does not have:
 * `Role.Member` grants nothing and no member ever signs in (see `Permission.kt`), there is no
 * push infrastructure on any platform, and the WhatsApp send job is separately parked (see the
 * reminder queue). [AnnouncementStatus.PUBLISHED] therefore means "staff have signed off on this
 * copy," not "members have seen it" — the same honest gap the reminder *queue* has always had
 * ahead of a working send job, built here for the same reason: the management layer is real and
 * useful on its own, and the delivery mechanism is future work rather than a guess.
 *
 * [audience] and [recipientCountAtPublish] follow from that: the count is real — computed from
 * actual member and subscription data — but it names who *matches the criteria*, not who was
 * reached, because nothing currently reaches them.
 */
data class Announcement(
    val id: AnnouncementId,
    val title: String,
    val body: String,
    val audience: AnnouncementAudience,
    /** The export's optional event card. Both null or both set — see `IntakeValidator` for the
     * same reasoning against a half-entered date elsewhere in this app. */
    val eventDate: LocalDate?,
    val eventTime: LocalTime?,
    val status: AnnouncementStatus,
    /** Resolved against `staff` at read time, the same as `GymClass.instructorStaffId`. */
    val createdByStaffId: String?,
    val createdAt: Instant,
    val publishedAt: Instant?,
    /**
     * A snapshot taken at the moment of publishing, frozen from then on — membership counts
     * change daily, and re-deriving this later would make a September announcement claim an
     * August audience size. Null while still a draft.
     */
    val recipientCountAtPublish: Int?,
) {
    val isDraft: Boolean get() = status == AnnouncementStatus.DRAFT
    val hasEvent: Boolean get() = eventDate != null
}

enum class AnnouncementStatus {
    DRAFT,
    PUBLISHED,
}

/**
 * Who a bulletin is meant for. Three of the export's five segments, not all five: "Women's
 * classes" and "Therapy patients" have no real recipient list behind them.
 * `:feature:classes` schedules recurring slots with no per-member enrolment or roster at all —
 * a "reaches N" count for it would be invented. Therapy cases exist per member, but a case being
 * open is clinical status; using it as a broadcast-targeting criterion, even for a broadcast that
 * currently reaches nobody, folds clinical data into a marketing list in a way nothing else in
 * this app does, and it is not a call to make by default.
 */
enum class AnnouncementAudience {
    ALL_MEMBERS,
    ACTIVE_ONLY,

    /** Their current term ends within the calendar month containing `today`. */
    EXPIRING_THIS_MONTH,
}

/**
 * Resolves [AnnouncementAudience] against real membership data — pure, so the composer's live
 * "Reaches N members" figure and the count frozen at publish time can never disagree.
 */
object AnnouncementReach {

    fun matching(
        audience: AnnouncementAudience,
        members: List<Member>,
        currentTerms: Map<MemberId, SubscriptionTerm>,
        today: LocalDate,
    ): List<Member> = when (audience) {
        AnnouncementAudience.ALL_MEMBERS -> members

        AnnouncementAudience.ACTIVE_ONLY ->
            members.filter { it.status == MembershipStatus.ACTIVE }

        AnnouncementAudience.EXPIRING_THIS_MONTH -> members.filter { member ->
            val term = currentTerms[member.id] ?: return@filter false
            term.endsOn.year == today.year && term.endsOn.month == today.month &&
                term.endsOn >= today
        }
    }

    fun count(
        audience: AnnouncementAudience,
        members: List<Member>,
        currentTerms: Map<MemberId, SubscriptionTerm>,
        today: LocalDate,
    ): Int = matching(audience, members, currentTerms, today).size
}
