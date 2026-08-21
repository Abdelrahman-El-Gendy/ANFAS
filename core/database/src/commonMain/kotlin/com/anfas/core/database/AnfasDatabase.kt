package com.anfas.core.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [
        PlaceholderEntity::class,
        MemberEntity::class,
        ReminderEntity::class,
        SubscriptionPlanEntity::class,
        SubscriptionEntity::class,
        IntakeBatchEntity::class,
        IntakeRowEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // v2 adds the `members` table. Adding a table needs no hand-written logic, but the
    // migration is declared rather than falling back to a destructive recreate — the
    // committed schemas in schemas/ are the contract, and dropping user data is never the
    // default here.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        // v3 adds reminders, subscription_plans and subscriptions — all new tables, so no
        // hand-written logic, but still declared rather than falling back to a destructive
        // recreate.
        AutoMigration(from = 2, to = 3),
        // v4 adds intake_batches and intake_rows. Also new tables — but note intake_rows
        // carries a CASCADE foreign key, so its creation order matters and Room handles that
        // only because the parent is declared in the same migration.
        AutoMigration(from = 3, to = 4),
    ],
)
@ConstructedBy(AnfasDatabaseConstructor::class)
abstract class AnfasDatabase : RoomDatabase() {
    abstract fun placeholderDao(): PlaceholderDao

    abstract fun memberDao(): MemberDao

    abstract fun reminderDao(): ReminderDao

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun intakeDao(): IntakeDao

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
