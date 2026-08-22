package com.anfas.core.model

import kotlinx.datetime.LocalDate

/**
 * Which memberships need attention, and when.
 *
 * Pure, and separate from the dashboard that shows it, because "expiring soon" is a domain
 * judgement rather than a screen concern — the same rule drives the profile's pill, and having
 * two definitions of *soon* is how a dashboard ends up disagreeing with the member it links to.
 */
object RenewalQueue {

    /**
     * Terms that have expired or will within [TermProgress.EXPIRING_SOON_DAYS], soonest first.
     *
     * Expired terms are included, not filtered out. A membership that lapsed last week is more
     * urgent than one lapsing tomorrow, and a queue that hides it means nobody chases it — which
     * is exactly the money the gym has already lost.
     */
    fun needingAttention(terms: List<SubscriptionTerm>, today: LocalDate): List<ExpiringTerm> =
        terms
            .map { ExpiringTerm(term = it, progress = TermProgress.of(it, today)) }
            .filter { it.needsAttention }
            .sortedBy { it.term.endsOn }

    /** Count only, for the dashboard tile. */
    fun countNeedingAttention(terms: List<SubscriptionTerm>, today: LocalDate): Int =
        needingAttention(terms, today).size
}

data class ExpiringTerm(val term: SubscriptionTerm, val progress: TermProgress) {
    val needsAttention: Boolean
        get() = progress.state == TermProgress.State.ExpiringSoon ||
            progress.state == TermProgress.State.Expired

    /** Already lapsed, so the member cannot train today. Rendered more urgently. */
    val hasExpired: Boolean get() = progress.state == TermProgress.State.Expired
}
