package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.MemberId
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {

    /** The purchasable plans, cheapest first. */
    fun observePlans(): Flow<AppResult<List<SubscriptionPlan>>>

    /** The member's latest term, or null if they have never subscribed. */
    fun observeCurrentTerm(memberId: MemberId): Flow<AppResult<SubscriptionTerm?>>

    /**
     * Every member's current term, soonest to expire first. Drives the dashboard's renewal queue.
     *
     * Deliberately not filtered by date here: "expiring soon" depends on *today*, which storage
     * must not reach for, and the caller already has [com.anfas.core.model.TermProgress] to
     * decide it.
     */
    fun observeCurrentTerms(): Flow<AppResult<List<SubscriptionTerm>>>

    /**
     * Records a confirmed renewal as a new term.
     *
     * Takes the [quote] the member was actually shown rather than re-deriving the price here,
     * so a plan price changing between quoting and confirming cannot alter what is charged.
     */
    suspend fun confirmRenewal(
        memberId: MemberId,
        quote: RenewalQuote,
        termId: String,
        confirmedAtEpochMs: Long,
    ): AppResult<SubscriptionTerm>

    suspend fun upsertPlans(plans: List<SubscriptionPlan>): AppResult<Unit>
}
