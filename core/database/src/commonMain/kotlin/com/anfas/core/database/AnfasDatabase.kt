package com.anfas.core.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import com.anfas.core.database.migrations.DropPlaceholderTable

@Database(
    entities = [
        MemberEntity::class,
        ReminderEntity::class,
        SubscriptionPlanEntity::class,
        SubscriptionEntity::class,
        IntakeBatchEntity::class,
        IntakeRowEntity::class,
        StaffEntity::class,
        CheckInEntity::class,
        GymClassEntity::class,
        TherapyCaseEntity::class,
        TherapySessionEntity::class,
        AnnouncementEntity::class,
    ],
    version = 10,
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
        // v5 drops `placeholder`, which only ever existed to prove KSP codegen worked on every
        // target. @DeleteTable needs the spec below because Room cannot tell a dropped table
        // from a renamed one.
        AutoMigration(from = 4, to = 5, spec = DropPlaceholderTable::class),
        // v6 adds `staff` for local sign-in. A new table, so no hand-written logic — but note
        // that an existing install has zero staff rows after this migration, which is exactly
        // the state the first-run setup screen handles. Migrating must never invent an account.
        AutoMigration(from = 5, to = 6),
        // v7 adds `check_ins`. A new table, so no hand-written logic — and deliberately with no
        // foreign key to members: deleting a member must not cascade away the record of them
        // having been here.
        AutoMigration(from = 6, to = 7),
        // v8 adds `scheduled_classes` for the weekly timetable. A new table again — and note it
        // carries no foreign key to `staff`, so its creation order does not matter: disabling a
        // coach must never delete next week's classes.
        AutoMigration(from = 7, to = 8),
        // v9 adds `therapy_cases` and `therapy_sessions`. New tables, but note the FK shape is
        // the opposite of check-ins: a case CASCADEs from its member, because clinical narrative
        // with no member to attach to is not a record worth keeping.
        AutoMigration(from = 8, to = 9),
        // v10 adds `announcements`. A new table, no foreign keys: it names an audience segment,
        // not a per-member recipient row.
        AutoMigration(from = 9, to = 10),
    ],
)
@ConstructedBy(AnfasDatabaseConstructor::class)
abstract class AnfasDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao

    abstract fun reminderDao(): ReminderDao

    abstract fun subscriptionDao(): SubscriptionDao

    abstract fun intakeDao(): IntakeDao

    abstract fun staffDao(): StaffDao

    abstract fun checkInDao(): CheckInDao

    abstract fun gymClassDao(): GymClassDao

    abstract fun therapyCaseDao(): TherapyCaseDao

    abstract fun announcementDao(): AnnouncementDao

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
