package com.anfas.core.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [PlaceholderEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AnfasDatabaseConstructor::class)
abstract class AnfasDatabase : RoomDatabase() {
    abstract fun placeholderDao(): PlaceholderDao

    companion object {
        const val FILE_NAME: String = "anfas.db"
    }
}

/**
 * Required on Kotlin Multiplatform: Room cannot use reflection on native targets, so the
 * `actual` implementation of this object is what KSP generates per target. Omitting it
 * produces confusing iOS-only compile failures.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AnfasDatabaseConstructor : RoomDatabaseConstructor<AnfasDatabase> {
    override fun initialize(): AnfasDatabase
}
