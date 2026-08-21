package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.common.AppResult
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeIssue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class OfflineFirstIntakeRepositoryTest {

    @Test
    fun `reading a batch validates it against the current membership`() = runTest {
        val fixture = fixture(
            rows = listOf(
                rowEntity("r1", ordinal = 1, phone = "555-0192"),
                rowEntity("r2", ordinal = 2, phone = "555-0889"),
            ),
            existingMembers = listOf(memberEntity("m1", "Chloe Davis", phone = "555-0889")),
        )

        fixture.repository.observeBatch(IntakeBatchId("b1")).test {
            val batch = assertNotNullBatch(awaitItem())
            assertEquals(1, batch.importableRows.size)
            val blocked = batch.blockedRows.single()
            assertTrue(IntakeIssue.DUPLICATE_PHONE in blocked.issues)
        }
    }

    @Test
    fun `a phone becoming a duplicate after the scan is caught on the next read`() = runTest {
        val members = FakeMemberDao()
        val fixture = fixture(rows = listOf(rowEntity("r1", phone = "555-0192")), memberDao = members)

        fixture.repository.observeBatch(IntakeBatchId("b1")).test {
            assertEquals(1, assertNotNullBatch(awaitItem()).importableRows.size)

            // Somebody registers with that number in the meantime. Validation is recomputed on
            // read, so the row must stop being importable without anything touching the sheet.
            members.upsertAll(listOf(memberEntity("m9", "Someone Else", phone = "555-0192")))

            assertEquals(0, assertNotNullBatch(awaitItem()).importableRows.size)
        }
    }

    @Test
    fun `editing a cell marks it human entered and clears its review marker`() = runTest {
        val fixture = fixture(
            rows = listOf(rowEntity("r1", plan = "Platnum", confidence = 0.2f)),
        )

        assertIs<AppResult.Success<Unit>>(
            fixture.repository.editField(
                rowId = com.anfas.core.model.IntakeRowId("r1"),
                field = IntakeFieldKey.PLAN,
                value = "Annual",
            ),
        )

        fixture.repository.observeBatch(IntakeBatchId("b1")).test {
            val row = assertNotNullBatch(awaitItem()).rows.single()
            assertEquals("Annual", row.plan.value)
            assertTrue(row.plan.wasEdited)
            assertTrue(!row.plan.needsReview)
            assertTrue(IntakeIssue.UNKNOWN_PLAN !in row.issues)
        }
    }

    @Test
    fun `importing registers only the importable rows and reports both counts`() = runTest {
        val members = FakeMemberDao(listOf(memberEntity("m1", "Chloe Davis", phone = "555-0889")))
        val fixture = fixture(
            rows = listOf(
                rowEntity("r1", ordinal = 1, name = "Alex Thompson", phone = "555-0192"),
                rowEntity("r2", ordinal = 2, name = "Chloe Davis", phone = "555-0889"),
                rowEntity("r3", ordinal = 3, name = "", phone = "555-0271"),
                rowEntity("r4", ordinal = 4, name = "Ben Carter", phone = "555-0653"),
            ),
            memberDao = members,
        )

        val outcome = assertIs<AppResult.Success<ImportOutcome>>(
            fixture.repository.importBatch(IntakeBatchId("b1")),
        ).value

        assertEquals(2, outcome.imported)
        assertEquals(2, outcome.skipped)

        val imported = members.current.map { it.fullName }.toSet()
        assertTrue("Alex Thompson" in imported)
        assertTrue("Ben Carter" in imported)
        // The blocked rows must not have been registered.
        assertEquals(3, members.current.size)
    }

    @Test
    fun `imported members get sequential numbers past the highest existing one`() = runTest {
        val members = FakeMemberDao(listOf(memberEntity("m1", "Existing", number = "#88392")))
        val fixture = fixture(
            rows = listOf(
                rowEntity("r1", ordinal = 1, name = "Alex Thompson", phone = "555-0192"),
                rowEntity("r2", ordinal = 2, name = "Ben Carter", phone = "555-0653"),
            ),
            memberDao = members,
        )

        fixture.repository.importBatch(IntakeBatchId("b1"))

        val issued = members.current
            .filter { it.id != "m1" }
            .map { it.membershipNumber }
            .sorted()
        assertEquals(listOf("#88393", "#88394"), issued)
    }

    @Test
    fun `an imported member carries the phone through in normalised form`() = runTest {
        val members = FakeMemberDao()
        val fixture = fixture(
            rows = listOf(rowEntity("r1", name = "Alex Thompson", phone = "+20 100 123 4567")),
            memberDao = members,
        )

        fixture.repository.importBatch(IntakeBatchId("b1"))

        val imported = members.current.single()
        assertEquals("+20 100 123 4567", imported.phone)
        // The normalised column is what intake's duplicate check compares against, so it must
        // be populated by the same rule the validator uses.
        assertEquals("01001234567", imported.phoneNormalised)
    }

    @Test
    fun `a batch marks itself imported so it cannot be imported twice`() = runTest {
        val fixture = fixture(rows = listOf(rowEntity("r1", phone = "555-0192")))

        assertIs<AppResult.Success<ImportOutcome>>(fixture.repository.importBatch(IntakeBatchId("b1")))
        assertEquals(IntakeBatchStatus.IMPORTED.name, fixture.intakeDao.currentBatches.single().status)

        // Second attempt is refused rather than duplicating everybody.
        assertIs<AppResult.Failure>(fixture.repository.importBatch(IntakeBatchId("b1")))
    }

    @Test
    fun `importing a sheet where nothing is importable registers nobody`() = runTest {
        val members = FakeMemberDao()
        val fixture = fixture(
            rows = listOf(rowEntity("r1", name = "", phone = "")),
            memberDao = members,
        )

        val outcome = assertIs<AppResult.Success<ImportOutcome>>(
            fixture.repository.importBatch(IntakeBatchId("b1")),
        ).value

        assertEquals(0, outcome.imported)
        assertEquals(1, outcome.skipped)
        assertTrue(members.current.isEmpty())
    }

    @Test
    fun `discarding a batch leaves the membership alone`() = runTest {
        val members = FakeMemberDao()
        val fixture = fixture(rows = listOf(rowEntity("r1", phone = "555-0192")), memberDao = members)

        assertIs<AppResult.Success<Unit>>(fixture.repository.discardBatch(IntakeBatchId("b1")))

        assertEquals(IntakeBatchStatus.DISCARDED.name, fixture.intakeDao.currentBatches.single().status)
        assertTrue(members.current.isEmpty())
    }

    @Test
    fun `creating a batch stores its rows with it`() = runTest {
        val intakeDao = FakeIntakeDao()
        val repository = OfflineFirstIntakeRepository(
            intakeDao = intakeDao,
            memberDao = FakeMemberDao(),
            newId = { "generated" },
            clock = FixedClock,
        )

        val batch = IntakeBatch(
            id = IntakeBatchId("new"),
            capturedAt = FixedClock.now(),
            sourceImageUri = "file://sheet.jpg",
            status = IntakeBatchStatus.REVIEWING,
            rows = listOf(rowEntity("r1").toDomainForTest()),
        )
        assertIs<AppResult.Success<Unit>>(repository.createBatch(batch))

        assertEquals(1, intakeDao.currentBatches.size)
        assertEquals(1, intakeDao.currentRows.size)
        assertEquals("new", intakeDao.currentRows.single().batchId)
    }

    @Test
    fun `a storage failure becomes a Failure rather than an exception`() = runTest {
        val fixture = fixture(rows = listOf(rowEntity("r1")))
        fixture.intakeDao.failure = IllegalStateException("database is locked")

        val result = fixture.repository.importBatch(IntakeBatchId("b1"))
        assertIs<AppResult.Failure>(result)
        assertTrue(result.error.message.contains("database is locked"))
    }

    // --- helpers --------------------------------------------------------------------------

    private class Fixture(
        val repository: IntakeRepository,
        val intakeDao: FakeIntakeDao,
    )

    private fun fixture(
        rows: List<com.anfas.core.database.IntakeRowEntity>,
        existingMembers: List<com.anfas.core.database.MemberEntity> = emptyList(),
        memberDao: FakeMemberDao = FakeMemberDao(existingMembers),
    ): Fixture {
        val intakeDao = FakeIntakeDao(batches = listOf(batchEntity()), rows = rows)
        // Must be unique per call: ids are the member primary key, so a constant would make
        // the upsert silently collapse every imported row into one member.
        var counter = 0
        return Fixture(
            repository = OfflineFirstIntakeRepository(
                intakeDao = intakeDao,
                memberDao = memberDao,
                newId = { "generated-${counter++}" },
                clock = FixedClock,
            ),
            intakeDao = intakeDao,
        )
    }

    private fun assertNotNullBatch(result: AppResult<IntakeBatch?>): IntakeBatch {
        val value = assertIs<AppResult.Success<IntakeBatch?>>(result).value
        return requireNotNull(value) { "Expected a batch" }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
    }
}

/** Only for the create-batch test, which needs a domain row to hand in. */
private fun com.anfas.core.database.IntakeRowEntity.toDomainForTest() =
    com.anfas.core.model.IntakeRow(
        id = com.anfas.core.model.IntakeRowId(id),
        ordinal = ordinal,
        name = com.anfas.core.model.IntakeField(name.value, name.confidence),
        phone = com.anfas.core.model.IntakeField(phone.value, phone.confidence),
        startDate = com.anfas.core.model.IntakeField(startDate.value, startDate.confidence),
        endDate = com.anfas.core.model.IntakeField(endDate.value, endDate.confidence),
        plan = com.anfas.core.model.IntakeField(plan.value, plan.confidence),
        issues = emptySet(),
        bounds = null,
    )
