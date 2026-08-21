package com.anfas.app.di

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.DefaultAppDispatchers
import com.anfas.core.common.logger
import com.anfas.core.data.dataModule
import com.anfas.core.i18n.i18nModule
import com.anfas.feature.announcements.AnnouncementsModule
import com.anfas.feature.classes.ClassesModule
import com.anfas.feature.equipment.EquipmentModule
import com.anfas.feature.intakeocr.IntakeOcrModule
import com.anfas.feature.members.MembersModule
import com.anfas.feature.subscriptions.SubscriptionsModule
import com.anfas.feature.therapy.TherapyModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val coreModule: Module = module {
    single<AppDispatchers> { DefaultAppDispatchers }
}

/**
 * Every feature's Koin module, collected in the one place that is allowed to see them all.
 * Add a feature here when you add it to :composeApp's dependencies.
 */
val featureModules: List<Module> = listOf(
    MembersModule,
    SubscriptionsModule,
    IntakeOcrModule,
    TherapyModule,
    ClassesModule,
    AnnouncementsModule,
    EquipmentModule,
)

/**
 * Platform launchers call this once at startup. [declaration] is where Android passes
 * `androidContext(...)`.
 */
fun initKoin(declaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    // One line at startup, deliberately. It proves the logging pipeline works end to end (on
    // desktop it is what creates the log file), and it is the first thing you want when reading
    // a user's log. No PII: the domain is member names and phone numbers, and those must never
    // be logged.
    logger("Startup").i("ANFAS starting: DI graph initialising")
    declaration()
    modules(coreModule)
    modules(dataModule)
    modules(i18nModule)
    modules(featureModules)
}
