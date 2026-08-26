package com.anfas.feature.subscriptions

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the subscriptions feature. Factories only — components own a coroutine scope
 * tied to their Decompose lifecycle and must never be singletons.
 */
val SubscriptionsModule: Module = module {
    factory {
        ReminderQueueComponentFactory(
            reminders = get(),
            scheduler = get(),
            auth = get(),
            dispatchers = get(),
        )
    }
    factory {
        RenewalSheetComponentFactory(
            members = get(),
            subscriptions = get(),
            dispatchers = get(),
        )
    }
}
