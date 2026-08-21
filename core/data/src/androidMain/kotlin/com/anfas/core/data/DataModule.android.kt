package com.anfas.core.data

import com.anfas.core.database.DatabaseBuilderFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Requires `androidContext(...)` in the launcher's `initKoin { }` declaration. */
internal actual fun platformDatabaseModule(): Module = module {
    single { DatabaseBuilderFactory(androidContext()) }
}
