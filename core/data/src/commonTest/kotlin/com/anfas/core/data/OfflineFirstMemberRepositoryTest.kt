package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
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
        val repo = OfflineFirstMemberRepository(dao)

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
        val repo = OfflineFirstMemberRepository(dao)
        val checkedIn = Instant.fromEpochMilliseconds(1_700_000_000_000)

        val stored = listOf(
            member(id = "1", name = "Ali Hassan", lastCheckInAt = checkedIn),
            member(id = "2", name = "Zara Ahmed", lastCheckInAt = null),
        )
        assertIs<AppResult.Success<Unit>>(repo.upsert(stored))

        repo.observeMembers().test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())
            @Suppress("UNCHECKED_CAST")
            val members = (result.value as List<com.anfas.core.model.Member>).associateBy { it.id.value }
            assertEquals(checkedIn, members.getValue("1").lastCheckInAt)
            assertNull(members.getValue("2").lastCheckInAt)
        }
    }

    @Test
    fun `delete removes only the requested member`() = runTest {
        val dao = FakeMemberDao(listOf(memberEntity("1", "Ali Hassan"), memberEntity("2", "Zara Ahmed")))
        val repo = OfflineFirstMemberRepository(dao)

        assertIs<AppResult.Success<Unit>>(repo.delete(MemberId("1")))

        repo.observeMembers().test {
            val result = assertIs<AppResult.Success<*>>(awaitItem())
            @Suppress("UNCHECKED_CAST")
            val members = result.value as List<com.anfas.core.model.Member>
            assertEquals(listOf("Zara Ahmed"), members.map { it.fullName })
        }
    }

    private fun repository(vararg rows: com.anfas.core.database.MemberEntity) =
        OfflineFirstMemberRepository(FakeMemberDao(rows.toList()))

    private fun member(
        id: String,
        name: String,
        lastCheckInAt: Instant?,
    ) = com.anfas.core.model.Member(
        id = MemberId(id),
        fullName = name,
        membershipNumber = "#$id",
        phone = null,
        status = MembershipStatus.ACTIVE,
        lastCheckInAt = lastCheckInAt,
        avatarUrl = null,
    )
}
