package com.anfas.core.database

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the v4 -> v5 auto-migration for real, rather than trusting that @DeleteTable did what
 * it says.
 *
 * Builds a v4-shaped database by hand from the committed 4.json DDL -- including the
 * room_master_table identity hash, without which Room refuses to recognise the file as v4 --
 * writes rows into both `placeholder` and `members`, then opens the database at v5 and asserts
 * that the dead table is gone and the real data survived.
 *
 * This is the only proof that a real gym's member list is not dropped on upgrade. The DDL below
 * is copied from schemas/.../4.json deliberately: if that file is ever edited, this test should
 * stop matching it and fail.
 */
class MigrationV4ToV5Test {

    private val v4Ddl = listOf(
        """CREATE TABLE IF NOT EXISTS `placeholder` (`id` INTEGER NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        """CREATE TABLE IF NOT EXISTS `members` (`id` TEXT NOT NULL, `fullName` TEXT NOT NULL, `membership_number` TEXT NOT NULL, `phone` TEXT, `phone_normalised` TEXT, `status` TEXT NOT NULL, `lastCheckInAtEpochMs` INTEGER, `avatarUrl` TEXT, PRIMARY KEY(`id`))""",
        """CREATE UNIQUE INDEX IF NOT EXISTS `index_members_membership_number` ON `members` (`membership_number`)""",
        """CREATE INDEX IF NOT EXISTS `index_members_phone_normalised` ON `members` (`phone_normalised`)""",
        """CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `member_name` TEXT NOT NULL, `phone` TEXT NOT NULL, `template` TEXT NOT NULL, `scheduled_at_epoch_ms` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `status` TEXT NOT NULL, `failure_reason` TEXT, `failure_provider_code` INTEGER, `failure_last_attempt_epoch_ms` INTEGER, `failure_detail` TEXT, PRIMARY KEY(`id`))""",
        """CREATE INDEX IF NOT EXISTS `index_reminders_status_scheduled_at_epoch_ms` ON `reminders` (`status`, `scheduled_at_epoch_ms`)""",
        """CREATE INDEX IF NOT EXISTS `index_reminders_member_id` ON `reminders` (`member_id`)""",
        """CREATE TABLE IF NOT EXISTS `subscription_plans` (`id` TEXT NOT NULL, `tier` TEXT NOT NULL, `price_minor_units` INTEGER NOT NULL, `currency` TEXT NOT NULL, `perks` TEXT NOT NULL, `savings_percent` INTEGER, PRIMARY KEY(`id`))""",
        """CREATE TABLE IF NOT EXISTS `subscriptions` (`id` TEXT NOT NULL, `member_id` TEXT NOT NULL, `plan_id` TEXT NOT NULL, `tier` TEXT NOT NULL, `starts_on_epoch_day` INTEGER NOT NULL, `ends_on_epoch_day` INTEGER NOT NULL, `payment_method` TEXT NOT NULL, `paid_minor_units` INTEGER NOT NULL, `currency` TEXT NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))""",
        """CREATE INDEX IF NOT EXISTS `index_subscriptions_member_id_ends_on_epoch_day` ON `subscriptions` (`member_id`, `ends_on_epoch_day`)""",
        """CREATE TABLE IF NOT EXISTS `intake_batches` (`id` TEXT NOT NULL, `captured_at_epoch_ms` INTEGER NOT NULL, `source_image_uri` TEXT, `status` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        """CREATE TABLE IF NOT EXISTS `intake_rows` (`id` TEXT NOT NULL, `batch_id` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `issues` TEXT NOT NULL, `bounds_left` REAL, `bounds_top` REAL, `bounds_right` REAL, `bounds_bottom` REAL, `name_value` TEXT NOT NULL, `name_confidence` REAL NOT NULL, `name_was_edited` INTEGER NOT NULL, `phone_value` TEXT NOT NULL, `phone_confidence` REAL NOT NULL, `phone_was_edited` INTEGER NOT NULL, `start_date_value` TEXT NOT NULL, `start_date_confidence` REAL NOT NULL, `start_date_was_edited` INTEGER NOT NULL, `end_date_value` TEXT NOT NULL, `end_date_confidence` REAL NOT NULL, `end_date_was_edited` INTEGER NOT NULL, `plan_value` TEXT NOT NULL, `plan_confidence` REAL NOT NULL, `plan_was_edited` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`batch_id`) REFERENCES `intake_batches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        """CREATE INDEX IF NOT EXISTS `index_intake_rows_batch_id_ordinal` ON `intake_rows` (`batch_id`, `ordinal`)""",
        """CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)""",
        """INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '9cc07acb3ce1fb843dd4c0341e4ca2fd')""",
    )

    @Test
    fun `dropping placeholder preserves member data`() = runTest {
        val dbFile = File.createTempFile("anfas-migration", ".db").apply { delete() }
        try {
            createV4Database(dbFile)

            val db = Room.databaseBuilder<AnfasDatabase>(name = dbFile.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()

            // A suspend read forces the file open, which is what runs the migration.
            db.memberDao().observeNormalisedPhones().first()
            db.close()

            // Assert on the migrated file with the raw driver rather than Room internals.
            val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
            try {
                val tables = connection.tableNames()
                assertTrue("placeholder" !in tables, "placeholder should be dropped, saw $tables")
                assertTrue("members" in tables, "members must survive, saw $tables")
                assertEquals(
                    1,
                    connection.countOf("members"),
                    "the pre-existing member row must survive the migration",
                )
                assertEquals(5, connection.userVersion(), "schema version should now be 5")
            } finally {
                connection.close()
            }
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        }
    }

    private fun SQLiteConnection.tableNames(): List<String> =
        prepare("SELECT name FROM sqlite_master WHERE type='table'").use { stmt ->
            buildList { while (stmt.step()) add(stmt.getText(0)) }
        }

    private fun SQLiteConnection.countOf(table: String): Int =
        prepare("SELECT COUNT(*) FROM $table").use { stmt ->
            if (stmt.step()) stmt.getInt(0) else -1
        }

    private fun SQLiteConnection.userVersion(): Int = prepare("PRAGMA user_version").use { stmt ->
        if (stmt.step()) stmt.getInt(0) else -1
    }

    /** Writes a database that Room will recognise as exactly schema version 4. */
    private fun createV4Database(file: File) {
        val driver = BundledSQLiteDriver()
        val connection = driver.open(file.absolutePath)
        try {
            v4Ddl.forEach(connection::execSQL)
            // No manual room_master_table insert: 4.json's setupQueries already create that
            // table and insert the correct identity hash, and Room refuses to recognise the file
            // as v4 without it.
            connection.execSQL("PRAGMA user_version = 4")
            connection.execSQL("INSERT INTO placeholder (id, label) VALUES (1, 'dead weight')")
            connection.execSQL(
                "INSERT INTO members (id, fullName, membership_number, status) " +
                    "VALUES ('m1', 'Existing Member', '#88392', 'ACTIVE')",
            )
        } finally {
            connection.close()
        }
    }
}
