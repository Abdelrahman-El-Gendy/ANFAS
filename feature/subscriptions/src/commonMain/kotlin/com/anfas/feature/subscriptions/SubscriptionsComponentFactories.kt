package com.anfas.feature.subscriptions

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.ReminderRepository
import com.anfas.core.data.ReminderScheduler
import com.anfas.core.data.ReminderSender
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.MemberId
import com.anfas.core.model.SubscriptionTerm
import com.arkivanov.decompose.ComponentContext

/**
 * Lets :composeApp construct this feature's components without seeing their dependencies.
 * Same pattern as members — the router supplies only the context and the navigation callbacks.
 */
class ReminderQueueComponentFactory internal constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val sender: ReminderSender,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        onOpenMemberClicked: (MemberId) -> Unit,
        onCloseClicked: () -> Unit,
    ): ReminderQueueComponent = ReminderQueueComponent(
        componentContext = componentContext,
        repository = reminders,
        scheduler = scheduler,
        sender = sender,
        auth = auth,
        dispatchers = dispatchers,
        onOpenMemberClicked = onOpenMemberClicked,
        onCloseClicked = onCloseClicked,
    )
}

class RenewalSheetComponentFactory internal constructor(
    private val members: MemberRepository,
    private val subscriptions: SubscriptionRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        memberId: MemberId,
        onRenewed: (SubscriptionTerm) -> Unit,
        onCancelled: () -> Unit,
    ): RenewalSheetComponent = RenewalSheetComponent(
        componentContext = componentContext,
        memberId = memberId,
        members = members,
        subscriptions = subscriptions,
        dispatchers = dispatchers,
        onRenewed = onRenewed,
        onCancelled = onCancelled,
    )
}
