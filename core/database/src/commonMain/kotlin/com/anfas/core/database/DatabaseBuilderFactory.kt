package com.anfas.core.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.DefaultAppDispatchers

/**
 * Resolves the platform database location and hands back a builder. Actuals differ in what
 * they need to construct: Android requires a Context, iOS and desktop do not.
 */
expect class DatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<AnfasDatabase>
}

/**
 * BundledSQLiteDriver ships SQLite compiled from source, so Android, iOS and desktop all run
 * the identical SQLite build instead of whatever the OS happens to provide.
 */
fun buildDatabase(
    factory: DatabaseBuilderFactory,
    dispatchers: AppDispatchers = DefaultAppDispatchers,
): AnfasDatabase = factory.create()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(dispatchers.io)
    .build()
