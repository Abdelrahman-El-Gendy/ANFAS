package com.anfas.core.data

import com.anfas.core.database.SyncOp
import com.anfas.core.database.SyncTables
import com.anfas.core.model.Currency
import com.anfas.core.model.MemberId
import com.anfas.core.model.Money
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import com.anfas.core.model.SubscriptionPlan
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The invariants that belong to no single repository.
 *
 * Per-write-path coverage lives beside each repository's own tests, where the fixtures already
 * are — see the `records an outbox entry` tests in `ClassRepositoryTest`, `EquipmentRepositoryTest`
 * and the rest. What is here is the cross-cutting set: the cascade, and the three places where
 * recording **nothing** is the correct behaviour and would otherwise read as a missed write path.
 *
 * On completeness, stated plainly rather than overclaimed: nothing in Kotlin/Native lets a test
 * enumerate the write methods of an interface, so "no path was missed" rests on two things — a
 * test per path, and the fact that no production code in `:core:data` calls an untracked DAO
 * writer any more. The second is checkable with a grep and is worth re-running when a write is
 * added; it is not enforced by the compiler.
 */
class SyncOutboxTest {

    // ---- the cascade ------------------------------------------------------------------------

    /**
     * Deleting a member tombstones the therapy rows SQLite is about to cascade away.
     *
     * This is the case a `deleted` flag could not have handled at all: SQLite does not cascade an
     * `UPDATE`, so flagging the member would leave the cases and sessions live beneath a
     * tombstoned parent. Deleting for real keeps the foreign keys working, at the cost of having
     * to read the children first — after the delete their ids are unknowable, which is precisely
     * what makes this unrecoverable-later and therefore worth recording now.
     */
    @Test
    fun `deleting a member tombstones the therapy rows that cascade with them`() = runTest {
        val dao = FakeMemberDao(listOf(memberEntity("m1", "Omar")))
        dao.therapyCaseIds["m1"] = listOf("case-1", "case-2")
        dao.therapySessionIds["case-1"] = listOf("s-1", "s-2")
        dao.therapySessionIds["case-2"] = listOf("s-3")
        val repository = OfflineFirstMemberRepository(dao) { "new" }

        repository.delete(MemberId("m1"))

        assertEquals(listOf("m1"), dao.sync.tombstoned(SyncTables.MEMBERS))
        assertEquals(
            listOf("case-1", "case-2"),
            dao.sync.tombstoned(SyncTables.THERAPY_CASES),
            "the cascaded cases were not tombstoned, so the other device would keep clinical " +
                "narrative attached to a member that no longer exists",
        )
        assertEquals(
            listOf("s-1", "s-2", "s-3"),
            dao.sync.tombstoned(SyncTables.THERAPY_SESSIONS),
            "the second cascade level was missed - sessions cascade from cases, not from members",
        )
        // Every tombstone is also a change to push, or the other device is never told about it.
        assertEquals(
            dao.sync.tombstones.map { "${it.tableName}/${it.rowId}" }.sorted(),
            dao.sync.changes.map { "${it.tableName}/${it.rowId}" }.sorted(),
            "a row was tombstoned locally but never queued to push - the other device would keep it",
        )
        assertTrue(dao.sync.changes.all { it.op == SyncOp.DELETE.name })
    }

    @Test
    fun `a member delete records the delete as a change and not as an upsert`() = runTest {
        val dao = FakeMemberDao(listOf(memberEntity("m1", "Omar")))
        val repository = OfflineFirstMemberRepository(dao) { "new" }

        repository.delete(MemberId("m1"))

        assertEquals(listOf(SyncTables.MEMBERS to SyncOp.DELETE.name), dao.sync.recorded)
    }

    // ---- where recording nothing is correct -------------------------------------------------

    /**
     * Reminders are deliberately never synced, so their writes file nothing.
     *
     * Four independent reasons in `design/sync-layer.md` §7, of which the decisive one is that
     * `existingIds`' own KDoc warns a SENT reminder must not be recreated as QUEUED — and sync is
     * a re-runner built on upsert. A SENT row arriving back as QUEUED sends a member a duplicate
     * renewal notice. Asserted rather than left implicit because an empty outbox here is
     * indistinguishable from a write path someone forgot.
     */
    @Test
    fun `a reminder write files nothing because reminders are never synced`() = runTest {
        val dao = FakeReminderDao()
        val repository = OfflineFirstReminderRepository(dao)

        repository.upsert(listOf(reminderFor("renewal:t1")))
        repository.retry(listOf(ReminderId("renewal:t1")))

        // Nothing to assert against but the type itself: ReminderDao has no recordChange, which
        // is the enforcement. If someone adds one, this test's comment is the reason not to.
        assertTrue(dao.current.isNotEmpty(), "the reminder itself should still have been written")
    }

    /**
     * Repointing a capture's local path is local-only maintenance and files nothing.
     *
     * `source_image_uri` is a device-absolute `file://` path. Pushed to another device it is a
     * non-null broken string, so the review pane there would not even fall back to its "no source
     * image" branch — it would show an empty pane. The design note's answer is a separate syncable
     * blob id; until then this column does not leave the device, and this is the first concrete
     * instance of that rule.
     */
    @Test
    fun `repointing a capture path files nothing because the path is local-only`() = runTest {
        val members = FakeMemberDao()
        val intake = FakeIntakeDao(members = members)
        val repository = OfflineFirstIntakeRepository(intake, members, { "id" })

        repository.relocateSourceImage(from = "file:///old/a.jpg", to = "file:///new/a.jpg")

        assertTrue(
            intake.sync.changes.isEmpty(),
            "a device-local file path was queued to push, which would send the other device a " +
                "broken non-null uri",
        )
    }

    // ---- the seed ---------------------------------------------------------------------------

    /**
     * The startup seed inserts what is absent and records only what it actually inserted.
     *
     * `SubscriptionPlanSeed` runs `createdAtStart`, so this is the write that happens on every
     * launch of every device. An upsert here would rewrite the catalogue each time — every app
     * start a conflict once plans sync, and an owner's price change reverted on the next launch.
     * An IGNOREd insert changed nothing, so filing an entry for it would push a row this device
     * did not write.
     */
    @Test
    fun `seeding plans twice writes and records only the first time`() = runTest {
        val dao = FakeSubscriptionDao(emptyList())
        val repository = OfflineFirstSubscriptionRepository(dao)
        val plans = listOf(plan("p1"), plan("p2"))

        repository.seedPlans(plans)
        val afterFirst = dao.sync.upserts(SyncTables.SUBSCRIPTION_PLANS)

        repository.seedPlans(plans)
        val afterSecond = dao.sync.upserts(SyncTables.SUBSCRIPTION_PLANS)

        assertEquals(listOf("p1", "p2"), afterFirst)
        assertEquals(
            afterFirst,
            afterSecond,
            "the second seed recorded changes again, so every app start would be a conflict and " +
                "an owner's price change would revert on the next launch",
        )
        assertEquals(2, dao.planRowsForSeed.size, "the second seed rewrote the catalogue")
    }

    private fun plan(id: String) = SubscriptionPlan(
        id = PlanId(id),
        tier = PlanTier.MONTHLY,
        price = Money(minorUnits = 50_000, currency = Currency.EGP),
        perks = "Full access",
        savingsPercent = null,
    )

    private fun reminderFor(id: String) = Reminder(
        id = ReminderId(id),
        memberId = MemberId("m1"),
        memberName = "Omar",
        phone = "01001112222",
        template = ReminderTemplate.REMINDER_AR,
        scheduledAt = Instant.fromEpochMilliseconds(0),
        attempts = 0,
        status = ReminderStatus.QUEUED,
        failure = null,
    )
}
