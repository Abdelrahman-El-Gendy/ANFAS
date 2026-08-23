package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One attempt to enter the gym, granted or refused.
 *
 * Recorded either way. A refused attempt is the more interesting record: it is the moment someone
 * was turned away, which staff get asked about later and which the design's `live-checkin-log`
 * shows as DENIED. Only storing successes would make the log a list of people who had no problem.
 *
 * [memberName] and [membershipNumber] are copied onto the row rather than joined at read time.
 * A check-in is a historical fact: if a member is later renamed or deleted, the log must still
 * say who walked in that afternoon.
 */
data class CheckIn(
    val id: CheckInId,
    /** Null when nobody could be identified — the design's "Unknown ID". */
    val memberId: MemberId?,
    val memberName: String,
    val membershipNumber: String,
    val at: Instant,
    val outcome: CheckInOutcome,
) {
    val wasGranted: Boolean get() = outcome == CheckInOutcome.GRANTED
}

/**
 * Why someone was let in or turned away.
 *
 * Distinct reasons rather than a boolean, because what staff do next differs: an expired
 * membership is a renewal conversation at the desk, a suspended one is a manager's decision, and
 * an unknown member is a registration.
 */
enum class CheckInOutcome {
    GRANTED,

    /** The term has run out. The most common denial, and the one that earns money to fix. */
    EXPIRED,

    /** Deliberately switched off — a decision to escalate, not to sell a renewal against. */
    SUSPENDED,

    /** Paused by agreement. Distinct from suspended: no fault, and resuming is routine. */
    PAUSED,

    /** No membership on record at all. */
    NO_MEMBERSHIP,
    ;

    val grantsEntry: Boolean get() = this == GRANTED
}

/**
 * Decides whether a member may enter, from what the app actually knows.
 *
 * Pure, and separate from recording the attempt, so the rule is testable without a database and
 * stated once. Both the member's [MembershipStatus] and their subscription term matter, and they
 * can disagree — a member marked ACTIVE whose term lapsed last month must still be refused, or
 * the gym gives away the renewal it is trying to sell.
 */
object CheckInPolicy {

    /**
     * @param term the member's current term, or null if they have never had one.
     */
    fun decide(member: Member, term: SubscriptionTerm?, today: LocalDate): CheckInOutcome {
        // Status first: a suspension or a pause is a deliberate decision about this person and
        // outranks whatever their dates say.
        when (member.status) {
            MembershipStatus.SUSPENDED -> return CheckInOutcome.SUSPENDED
            MembershipStatus.PAUSED -> return CheckInOutcome.PAUSED
            MembershipStatus.EXPIRED -> return CheckInOutcome.EXPIRED
            MembershipStatus.ACTIVE -> Unit
        }

        // Then the term. A member imported from a paper sheet is ACTIVE with no term yet, which
        // is not the same as having let one lapse -- so it is NO_MEMBERSHIP, and the desk sells
        // them a plan rather than telling them theirs expired.
        if (term == null) return CheckInOutcome.NO_MEMBERSHIP

        val progress = TermProgress.of(term, today)
        return when (progress.state) {
            TermProgress.State.Expired -> CheckInOutcome.EXPIRED

            // NotStarted is granted on purpose: a term bought in advance means they have paid.
            // Turning away a paying member because their plan starts on Monday is not a rule
            // anyone would defend at the desk.
            TermProgress.State.NotStarted,
            TermProgress.State.Active,
            TermProgress.State.ExpiringSoon,
            -> CheckInOutcome.GRANTED
        }
    }
}

/**
 * Today's figures for the check-in log.
 *
 * [peakHour] is the local hour with the most granted entries, or null before anyone has arrived.
 * Derived rather than stored: it changes as the day goes on.
 */
data class CheckInSummary(val granted: Int = 0, val denied: Int = 0, val peakHour: Int? = null) {
    val total: Int get() = granted + denied

    companion object {
        fun of(checkIns: List<CheckIn>, hourOf: (Instant) -> Int): CheckInSummary {
            if (checkIns.isEmpty()) return CheckInSummary()
            val granted = checkIns.filter { it.wasGranted }
            return CheckInSummary(
                granted = granted.size,
                denied = checkIns.size - granted.size,
                // Ties resolve to the earlier hour: "busiest since" reads better than an
                // arbitrary pick, and the earlier one is when the queue actually formed.
                peakHour = granted
                    .groupingBy { hourOf(it.at) }
                    .eachCount()
                    .entries
                    .sortedWith(
                        compareByDescending<Map.Entry<Int, Int>> {
                            it.value
                        }.thenBy { it.key },
                    )
                    .firstOrNull()
                    ?.key,
            )
        }
    }
}
