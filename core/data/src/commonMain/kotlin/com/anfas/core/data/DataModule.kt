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
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
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

    // `onClose` rather than a teardown call in each launcher: the definition that opens the
    // connection is the right place to close it, so no platform entry point has to remember.
    // Without this, quitting the desktop app left `anfas.db-wal`/`-shm` behind — SQLite recovers
    // from them on next open, but an uncheckpointed WAL is also what makes a file copied by a
    // backup tool an incomplete database.
    single<AnfasDatabase> { buildDatabase(factory = get(), dispatchers = get()) }
        .withOptions { onClose { it?.close() } }
    single { get<AnfasDatabase>().memberDao() }
    single { get<AnfasDatabase>().reminderDao() }
    single { get<AnfasDatabase>().subscriptionDao() }
    single { get<AnfasDatabase>().intakeDao() }
    single { get<AnfasDatabase>().staffDao() }
    single { get<AnfasDatabase>().checkInDao() }
    single { get<AnfasDatabase>().gymClassDao() }
    single { get<AnfasDatabase>().therapyCaseDao() }
    single { get<AnfasDatabase>().announcementDao() }
    single { get<AnfasDatabase>().equipmentDao() }

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
    single<CheckInRepository> {
        OfflineFirstCheckInRepository(
            dao = get(),
            memberDao = get(),
            subscriptionDao = get(),
            dispatchers = get(),
            // The device's zone, resolved once. "Today" for a gym is the local day, and the log
            // must not shuffle when a phone crosses a boundary mid-session.
            zone = TimeZone.currentSystemDefault(),
            newId = { Uuid.random().toString() },
        )
    }
    single<ClassRepository> {
        OfflineFirstClassRepository(classes = get(), staff = get(), dispatchers = get())
    }
    single<TherapyRepository> {
        OfflineFirstTherapyRepository(cases = get(), staff = get(), dispatchers = get())
    }
    single<AnnouncementRepository> {
        OfflineFirstAnnouncementRepository(
            announcements = get(),
            members = get(),
            subscriptions = get(),
            staff = get(),
            dispatchers = get(),
        )
    }
    single<EquipmentRepository> {
        OfflineFirstEquipmentRepository(equipment = get(), dispatchers = get())
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
