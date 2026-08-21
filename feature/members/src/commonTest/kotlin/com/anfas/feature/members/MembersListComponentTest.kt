package com.anfas.feature.members

import app.cash.turbine.test
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.MemberRepository
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MembersListComponentTest {

    @Test
    fun `an empty directory and a blank query is DirectoryEmpty not NoMatches`() = runTest {
        val component = component(members = emptyList())

        component.state.test {
            assertIs<MembersListContent.DirectoryEmpty>(awaitItem().content)
        }
    }

    @Test
    fun `a query that matches nothing is NoMatches and carries the term`() = runTest {
        val component = component(members = listOf(member("1", "Ali Hassan")))

        component.state.test {
            assertIs<MembersListContent.Loaded>(awaitItem().content)

            component.onQueryChanged("khaled")
            // The typed text is visible immediately, before the debounced search resolves.
            assertEquals("khaled", awaitItem().query)

            val noMatches = awaitItem().content
            assertIs<MembersListContent.NoMatches>(noMatches)
            assertEquals("khaled", noMatches.query)
        }
    }

    @Test
    fun `clearing the search returns to the full directory`() = runTest {
        val component = component(members = listOf(member("1", "Ali Hassan")))

        component.state.test {
            assertIs<MembersListContent.Loaded>(awaitItem().content)
            component.onQueryChanged("zzz")
            skipItems(1)
            assertIs<MembersListContent.NoMatches>(awaitItem().content)

            component.onClearSearch()
            val restored = awaitItem()
            assertEquals("", restored.query)
            assertIs<MembersListContent.Loaded>(awaitItem().content)
        }
    }

    @Test
    fun `a repository failure surfaces as Failed with the message`() = runTest {
        val component = component(
            members = emptyList(),
            result = AppResult.Failure(AppError.Storage("database is locked")),
        )

        component.state.test {
            val failed = assertIs<MembersListContent.Failed>(awaitItem().content)
            assertEquals("database is locked", failed.message)
        }
    }

    @Test
    fun `search filters by name and by membership number`() = runTest {
        val component = component(
            members = listOf(
                member("1", "Ali Hassan", number = "#88392"),
                member("2", "Zara Ahmed", number = "#12345"),
            ),
        )

        component.state.test {
            assertIs<MembersListContent.Loaded>(awaitItem().content)

            component.onQueryChanged("12345")
            skipItems(1)
            val loaded = assertIs<MembersListContent.Loaded>(awaitItem().content)
            assertEquals(listOf("Zara Ahmed"), loaded.members.map { it.fullName })
        }
    }

    // --- helpers -------------------------------------------------------------------------

    private fun kotlinx.coroutines.test.TestScope.component(
        members: List<Member>,
        result: AppResult<List<Member>>? = null,
    ): MembersListComponent {
        val lifecycle = LifecycleRegistry()
        val component = MembersListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = FakeMemberRepository(members, result),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onMemberClicked = {},
            onAddMemberClicked = {},
            onScanSheetClicked = {},
        )
        lifecycle.resume()
        return component
    }

    private fun member(
        id: String,
        name: String,
        number: String = "#$id",
    ) = Member(
        id = MemberId(id),
        fullName = name,
        membershipNumber = number,
        phone = null,
        status = MembershipStatus.ACTIVE,
        lastCheckInAt = null,
        avatarUrl = null,
    )
}

private class FakeMemberRepository(
    members: List<Member>,
    private val forcedResult: AppResult<List<Member>>?,
) : MemberRepository {

    private val rows = MutableStateFlow(members)

    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> = rows.map { list ->
        forcedResult ?: AppResult.Success(
            if (query.isBlank()) {
                list
            } else {
                list.filter {
                    it.fullName.contains(query, true) || it.membershipNumber.contains(query, true)
                }
            },
        )
    }

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> =
        rows.map { list -> AppResult.Success(list.firstOrNull { it.id == id }) }

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
