package com.anfas.core.data

import com.anfas.core.database.DatabaseBuilderFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformDatabaseModule(): Module = module {
    single { DatabaseBuilderFactory() }
}
