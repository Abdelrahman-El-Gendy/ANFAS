package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.database.SubscriptionDao
import com.anfas.core.database.SubscriptionEntity
import com.anfas.core.model.MemberId
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.SubscriptionId
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
import kotlinx.coroutines.flow.Flow

internal class OfflineFirstSubscriptionRepository(private val dao: SubscriptionDao) :
    SubscriptionRepository {

    override fun observePlans(): Flow<AppResult<List<SubscriptionPlan>>> =
        dao.observePlans().asAppResult("Could not load plans") { rows ->
            rows.map { it.toDomain() }
        }

    override fun observeCurrentTerm(memberId: MemberId): Flow<AppResult<SubscriptionTerm?>> =
        dao.observeCurrent(memberId.value)
            .asAppResult("Could not load the current subscription") { it?.toDomain() }

    override fun observeCurrentTerms(): Flow<AppResult<List<SubscriptionTerm>>> =
        dao.observeAllCurrent()
            .asAppResult("Could not load subscriptions") { rows -> rows.map { it.toDomain() } }

    override suspend fun confirmRenewal(
        memberId: MemberId,
        quote: RenewalQuote,
        termId: String,
        confirmedAtEpochMs: Long,
    ): AppResult<SubscriptionTerm> = runStorage("Could not confirm the renewal") {
        val entity = SubscriptionEntity(
            id = termId,
            memberId = memberId.value,
            planId = quote.plan.id.value,
            tier = quote.plan.tier.name,
            startsOnEpochDay = quote.startsOn.toEpochDays(),
            endsOnEpochDay = quote.endsOn.toEpochDays(),
            paymentMethod = quote.paymentMethod.name,
            paidMinorUnits = quote.total.minorUnits,
            currency = quote.total.currency.code,
            createdAtEpochMs = confirmedAtEpochMs,
        )
        dao.upsert(entity)
        SubscriptionTerm(
            id = SubscriptionId(termId),
            memberId = memberId,
            planId = quote.plan.id,
            tier = quote.plan.tier,
            startsOn = quote.startsOn,
            endsOn = quote.endsOn,
            paymentMethod = quote.paymentMethod,
            paid = quote.total,
        )
    }

    override suspend fun upsertPlans(plans: List<SubscriptionPlan>): AppResult<Unit> =
        runStorage("Could not save plans") { dao.upsertPlans(plans.map { it.toEntity() }) }
}
