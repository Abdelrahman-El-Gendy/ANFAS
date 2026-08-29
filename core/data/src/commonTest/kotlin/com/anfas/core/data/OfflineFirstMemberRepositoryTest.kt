package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.database.SyncTables
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class OfflineFirstMemberRepositoryTest {

    @Test
    fun `blank query returns the whole directory name-ordered`() = runTest {
        val repo = repository(
            memberEntity("2", "Zara Ahmed"),
            memberEntity("1", "Ali Hassan"),
        )

        repo.observeMembers("").test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())

            @Suppress("UNCHECKED_CAST")
            val names = (result.value as List<com.anfas.core.model.Member>).map { it.fullName }
            assertEquals(listOf("Ali Hassan", "Zara Ahmed"), names)
        }
    }

    @Test
    fun `query matches membership number as well as name`() = runTest {
        val repo = repository(
            memberEntity("1", "Ali Hassan", number = "#88392"),
            memberEntity("2", "Zara Ahmed", number = "#12345"),
        )

        repo.observeMembers("88392").test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())

            @Suppress("UNCHECKED_CAST")
            val members = result.value as List<com.anfas.core.model.Member>
            assertEquals(listOf("Ali Hassan"), members.map { it.fullName })
        }
    }

    @Test
    fun `an unknown status string degrades to PAUSED instead of throwing`() = runTest {
        val repo = repository(memberEntity("1", "Ali Hassan", status = "NOT_A_REAL_STATUS"))

        repo.observeMembers().test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())

            @Suppress("UNCHECKED_CAST")
            val members = result.value as List<com.anfas.core.model.Member>
            assertEquals(MembershipStatus.PAUSED, members.single().status)
        }
    }

    @Test
    fun `a storage failure becomes AppError Storage not an exception`() = runTest {
        val dao = FakeMemberDao(listOf(memberEntity("1", "Ali Hassan")))
        dao.failure = IllegalStateException("database is locked")
        val repo = repository(dao)

        repo.observeMembers().test {
            val failure = assertIs<AppResult.Failure>(awaitItem())
            assertIs<AppError.Storage>(failure.error)
            // The cause is kept — a bare "could not load" is undebuggable in the field.
            assertEquals(true, failure.error.message.contains("database is locked"))
            awaitComplete()
        }
    }

    @Test
    fun `round trip preserves the check-in instant and nullability`() = runTest {
        val dao = FakeMemberDao()
        val repo = repository(dao)
        val checkedIn = Instant.fromEpochMilliseconds(1_700_000_000_000)

        val stored = listOf(
            member(id = "1", name = "Ali Hassan", lastCheckInAt = checkedIn),
            member(id = "2", name = "Zara Ahmed", lastCheckInAt = null),
        )
        assertIs<AppResult.Success<Unit>>(repo.upsert(stored))

        repo.observeMembers().test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())

            @Suppress("UNCHECKED_CAST")
            val members = (result.value as List<com.anfas.core.model.Member>).associateBy {
                it.id.value
            }
            assertEquals(checkedIn, members.getValue("1").lastCheckInAt)
            assertNull(members.getValue("2").lastCheckInAt)
        }
    }

    @Test
    fun `delete removes only the requested member`() = runTest {
        val dao =
            FakeMemberDao(listOf(memberEntity("1", "Ali Hassan"), memberEntity("2", "Zara Ahmed")))
        val repo = repository(dao)

        assertIs<AppResult.Success<Unit>>(repo.delete(MemberId("1")))

        repo.observeMembers().test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())

            @Suppress("UNCHECKED_CAST")
            val members = result.value as List<com.anfas.core.model.Member>
            assertEquals(listOf("Zara Ahmed"), members.map { it.fullName })
        }
    }

    @Test
    fun `creating a member allocates the next number and stores them active`() = runTest {
        val dao = FakeMemberDao(listOf(memberEntity("1", "Ali Hassan")))
        val repo = repository(dao)

        val created = repo.create(fullName = "  Mona Khalil  ", phone = " 01001234567 ")

        val member = assertIs<AppResult.Success<Member>>(created).value
        // Trimmed on the way in, so a stray space cannot make two members look different.
        assertEquals("Mona Khalil", member.fullName)
        assertEquals("01001234567", member.phone)
        assertEquals(MembershipStatus.ACTIVE, member.status)
        // The existing fixture member is "#1", below MembershipNumbers.FIRST, so the sequence
        // starts rather than continuing from it — see MembershipNumbersTest.
        assertEquals("#10000", member.membershipNumber)
        assertEquals(2, dao.current.size)
    }

    /**
     * A walk-in can be registered without a phone. Storing "" instead of null would be a
     * distinct value that duplicate detection then has to special-case.
     */
    @Test
    fun `a blank phone becomes null`() = runTest {
        val repo = repository(FakeMemberDao(emptyList()))

        val member = assertIs<AppResult.Success<Member>>(repo.create("Walk In", "   ")).value

        assertEquals(null, member.phone)
        assertEquals("#10000", member.membershipNumber, "the first member starts the sequence")
    }

    /** Two members created in a row must not share a number or an id. */
    @Test
    fun `consecutive members get distinct numbers and ids`() = runTest {
        val dao = FakeMemberDao(emptyList())
        val repo = repository(dao)

        repo.create("First Member", null)
        repo.create("Second Member", null)

        val stored = dao.current
        assertEquals(2, stored.size)
        assertEquals(2, stored.map { it.membershipNumber }.distinct().size)
        assertEquals(2, stored.map { it.id }.distinct().size)
    }

    private fun repository(vararg rows: com.anfas.core.database.MemberEntity) =
        repository(FakeMemberDao(rows.toList()))

    private fun member(id: String, name: String, lastCheckInAt: Instant?) =
        com.anfas.core.model.Member(
            id = MemberId(id),
            fullName = name,
            membershipNumber = "#$id",
            phone = null,
            status = MembershipStatus.ACTIVE,
            lastCheckInAt = lastCheckInAt,
            avatarUrl = null,
        )

    /**
     * A counter, not a constant. A constant id made Room's upsert collapse every created member
     * into one row, which read as a product bug for a while — see MembershipNumbersTest.
     */

    /**
     * Both member write paths file an outbox entry. The delete path and its cascade are in
     * SyncOutboxTest, which is where the reasoning for all of this lives.
     */
    @Test
    fun `creating and upserting members each file an outbox entry`() = runTest {
        val dao = FakeMemberDao()
        val repository = repository(dao)

        val created = repository.create(fullName = "Omar Khaled", phone = null).valueOrFail()
        assertEquals(listOf(created.id.value), dao.sync.upserts(SyncTables.MEMBERS))

        repository.upsert(listOf(created.copy(fullName = "Omar K"))).valueOrFail()
        assertEquals(
            listOf(created.id.value, created.id.value),
            dao.sync.upserts(SyncTables.MEMBERS),
            "the bulk upsert filed nothing, so a member edited here would never reach the other device",
        )
    }

    private fun repository(dao: FakeMemberDao): MemberRepository {
        var next = 0
        return OfflineFirstMemberRepository(dao = dao, newId = { "m-${next++}" })
    }
}
