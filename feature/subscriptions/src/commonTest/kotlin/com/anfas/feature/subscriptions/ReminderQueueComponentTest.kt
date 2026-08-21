package com.anfas.feature.subscriptions

import app.cash.turbine.test
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.model.FailureReason
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderQueueComponentTest {

    @Test
    fun `opens on the Failed tab because that is the actionable one`() = runTest {
        val component = component(listOf(reminder("1")))

        component.state.test {
            val state = awaitItem()
            assertEquals(ReminderStatus.FAILED, state.selectedStatus)
            assertIs<ReminderQueueContent.Loaded>(state.content)
        }
    }

    @Test
    fun `an empty unfiltered tab reports the status so the message can be specific`() = runTest {
        val component = component(emptyList())

        component.state.test {
            val empty = assertIs<ReminderQueueContent.Empty>(awaitItem().content)
            assertEquals(ReminderStatus.FAILED, empty.status)
            assertTrue(!empty.isFiltered)
        }
    }

    @Test
    fun `an empty filtered tab is distinguishable from a genuinely clear queue`() = runTest {
        val component = component(listOf(reminder("1", name = "Omar Khaled")))

        component.state.test {
            assertIs<ReminderQueueContent.Loaded>(awaitItem().content)
            component.onQueryChanged("nobody")
            skipItems(1)
            val empty = assertIs<ReminderQueueContent.Empty>(awaitItem().content)
            assertTrue(empty.isFiltered)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching tabs clears the selection and any open modal`() = runTest {
        val component = component(listOf(reminder("1"), reminder("2")))

        component.state.test {
            awaitItem()
            component.onToggleSelected(ReminderId("1"))
            component.onOpenFailure(ReminderId("2"))
            assertTrue(awaitItem().selectedIds.isNotEmpty())
            awaitItem()

            component.onStatusSelected(ReminderStatus.SENT)
            val switched = awaitItem()
            assertEquals(ReminderStatus.SENT, switched.selectedStatus)
            assertTrue(switched.selectedIds.isEmpty())
            assertNull(switched.openedFailure)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `select all applies to the visible rows and toggles off again`() = runTest {
        val component = component(listOf(reminder("1"), reminder("2")))

        component.state.test {
            awaitItem()
            component.onToggleSelectAll()
            assertEquals(setOf(ReminderId("1"), ReminderId("2")), awaitItem().selectedIds)

            component.onToggleSelectAll()
            assertTrue(awaitItem().selectedIds.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selection cannot survive a row leaving the visible list`() = runTest {
        val component = component(
            listOf(
                reminder("1", name = "Omar Khaled"),
                reminder("2", name = "Sara Ahmed"),
            ),
        )

        component.state.test {
            awaitItem()
            component.onToggleSelectAll()
            assertEquals(2, awaitItem().selectedIds.size)

            // Filtering to one row must drop the other from the selection, so a bulk action
            // can never touch something off screen.
            component.onQueryChanged("Sara")
            skipItems(1)
            assertEquals(setOf(ReminderId("2")), awaitItem().selectedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a partial bulk retry says how many actually went`() = runTest {
        val component = component(
            listOf(
                reminder("1", reason = FailureReason.RATE_LIMITED),
                reminder("2", reason = FailureReason.NOT_OPTED_IN),
                reminder("3", reason = FailureReason.INVALID_PHONE_NUMBER),
            ),
        )

        component.state.test {
            awaitItem()
            component.onToggleSelectAll()
            awaitItem()
            component.onRetrySelected()

            val notice = awaitItemWithNotice()
            assertEquals("1 of 3 requeued; the rest need action first.", notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying only unretryable failures explains why nothing happened`() = runTest {
        val component = component(listOf(reminder("1", reason = FailureReason.NOT_OPTED_IN)))

        component.state.test {
            awaitItem()
            component.onRetry(ReminderId("1"))
            assertEquals(
                "Nothing to retry — these failures need action before they can be resent.",
                awaitItemWithNotice(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single successful retry reads in the singular`() = runTest {
        val component = component(listOf(reminder("1", reason = FailureReason.RATE_LIMITED)))

        component.state.test {
            awaitItem()
            component.onRetry(ReminderId("1"))
            assertEquals("Message requeued.", awaitItemWithNotice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repository failure surfaces as Failed`() = runTest {
        val component = component(
            emptyList(),
            forced = AppResult.Failure(AppError.Storage("database is locked")),
        )

        component.state.test {
            val failed = assertIs<ReminderQueueContent.Failed>(awaitItem().content)
            assertEquals("database is locked", failed.message)
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<ReminderQueueState>.awaitItemWithNotice(): String {
        repeat(6) {
            val next = awaitItem()
            next.notice?.let { return it }
        }
        error("No notice was emitted")
    }

    private fun TestScope.component(
        reminders: List<com.anfas.core.model.Reminder>,
        forced: AppResult<List<com.anfas.core.model.Reminder>>? = null,
    ): ReminderQueueComponent {
        val lifecycle = LifecycleRegistry()
        val component = ReminderQueueComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = FakeReminderRepository(reminders, forced),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onOpenMemberClicked = {},
        )
        lifecycle.resume()
        return component
    }
}
