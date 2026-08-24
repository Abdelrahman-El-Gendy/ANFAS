package com.anfas.feature.members

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.MemberId
import com.arkivanov.decompose.ComponentContext

/**
 * Lets :composeApp create a [MemberProfileComponent] without knowing what it depends on.
 *
 * Note this takes [SubscriptionRepository] as well as [MemberRepository]. That is not a feature
 * reaching across into another feature — both are `:core:data` seams, which is exactly what that
 * module exists for. `:feature:members` still has no dependency on `:feature:subscriptions`.
 */
class MemberProfileComponentFactory internal constructor(
    private val members: MemberRepository,
    private val subscriptions: SubscriptionRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        memberId: MemberId,
        onRenewClicked: (MemberId) -> Unit,
        onTherapyClicked: (MemberId) -> Unit,
        onBackClicked: () -> Unit,
    ): MemberProfileComponent = MemberProfileComponent(
        componentContext = componentContext,
        memberId = memberId,
        members = members,
        subscriptions = subscriptions,
        auth = auth,
        dispatchers = dispatchers,
        onRenewClicked = onRenewClicked,
        onTherapyClicked = onTherapyClicked,
        onBackClicked = onBackClicked,
    )
}
