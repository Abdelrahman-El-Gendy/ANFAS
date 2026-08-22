package com.anfas.feature.dashboard

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.ReminderRepository
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.MemberId
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Lets :composeApp create a [DashboardComponent] without seeing its three seams. */
class DashboardComponentFactory internal constructor(
    private val members: MemberRepository,
    private val subscriptions: SubscriptionRepository,
    private val reminders: ReminderRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        onMemberClicked: (MemberId) -> Unit,
        onOpenReminders: () -> Unit,
    ): DashboardComponent = DashboardComponent(
        componentContext = componentContext,
        members = members,
        subscriptions = subscriptions,
        reminders = reminders,
        dispatchers = dispatchers,
        onMemberClicked = onMemberClicked,
        onOpenReminders = onOpenReminders,
    )
}

val DashboardModule: Module = module {
    factory {
        DashboardComponentFactory(
            members = get(),
            subscriptions = get(),
            reminders = get(),
            dispatchers = get(),
        )
    }
}
