package com.anfas.core.data

import com.anfas.core.auth.PasswordHasher
import com.anfas.core.auth.Pbkdf2PasswordHasher
import com.anfas.core.auth.SessionStore
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.database.AnfasDatabase
import com.anfas.core.database.buildDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.Uuid

/**
 * Wiring for the data layer. Everything is a `single`: the database holds an open connection
 * and must not be built twice.
 *
 * [platformDatabaseModule] is separate because constructing the database differs per platform
 * — Android needs a Context, iOS and desktop resolve a directory themselves.
 */
val dataModule: Module = module {
    includes(platformDatabaseModule())

    single<AnfasDatabase> { buildDatabase(factory = get(), dispatchers = get()) }
    single { get<AnfasDatabase>().memberDao() }
    single { get<AnfasDatabase>().reminderDao() }
    single { get<AnfasDatabase>().subscriptionDao() }
    single { get<AnfasDatabase>().intakeDao() }
    single { get<AnfasDatabase>().staffDao() }

    single<MemberRepository> {
        OfflineFirstMemberRepository(dao = get(), newId = { Uuid.random().toString() })
    }
    single<ReminderRepository> { OfflineFirstReminderRepository(dao = get()) }
    single<SubscriptionRepository> { OfflineFirstSubscriptionRepository(dao = get()) }
    single<SessionStore> { SettingsSessionStore(settings = get()) }
    single<PasswordHasher> { Pbkdf2PasswordHasher() }
    single<AuthRepository> {
        OfflineFirstAuthRepository(
            dao = get(),
            sessionStore = get(),
            hasher = get(),
            dispatchers = get(),
        )
    }
    single<IntakeRepository> {
        OfflineFirstIntakeRepository(
            intakeDao = get(),
            memberDao = get(),
            newId = { Uuid.random().toString() },
        )
    }

    /**
     * Seeds the plan catalogue once at startup. `createdAtStart` so it runs without anything
     * having to ask for it, and on the IO dispatcher because it touches the database — Koin
     * builds singletons on whatever thread first resolves them, which on Android is the main
     * thread.
     */
    single(createdAtStart = true) {
        val repository = get<SubscriptionRepository>()
        val dispatchers = get<AppDispatchers>()
        CoroutineScope(
            dispatchers.io + SupervisorJob() + appExceptionHandler("PlanSeed"),
        ).also { scope ->
            scope.launch { repository.upsertPlans(SubscriptionPlanSeed.plans) }
        }
    }
}

internal expect fun platformDatabaseModule(): Module
