package com.anfas.feature.subscriptions

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.ReminderScheduler
import com.anfas.core.data.ReminderSender
import com.anfas.core.data.ScheduleOutcome
import com.anfas.core.data.SendRunOutcome
import com.anfas.core.model.FailureReason
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
private const val RETRY_EMISSIONS = 6

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

            // Asserting on the typed notice rather than English prose: the component no longer
            // formats sentences, and this is a stronger assertion than a string compare.
            assertEquals(QueueNotice.Requeued(requeued = 1, requested = 3), awaitItemWithNotice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retrying only unretryable failures explains why nothing happened`() = runTest {
        val component = component(listOf(reminder("1", reason = FailureReason.NOT_OPTED_IN)))

        component.state.test {
            awaitItem()
            component.onRetry(ReminderId("1"))
            assertEquals(QueueNotice.NothingRetryable, awaitItemWithNotice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a single successful retry reads in the singular`() = runTest {
        val component = component(listOf(reminder("1", reason = FailureReason.RATE_LIMITED)))

        component.state.test {
            awaitItem()
            component.onRetry(ReminderId("1"))
            assertEquals(QueueNotice.Requeued(requeued = 1, requested = 1), awaitItemWithNotice())
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

    private suspend fun TurbineTestContext<ReminderQueueState>.awaitItemWithNotice(): QueueNotice {
        repeat(6) {
            val next = awaitItem()
            next.notice?.let { return it }
        }
        error("No notice was emitted")
    }

    /**
     * Viewing the queue and re-sending from it are separate permissions: a retry sends a WhatsApp
     * message on the gym's account, which is not the same act as reading who failed. The route
     * guard cannot catch this because both live on this one screen.
     */
    @Test
    fun `a session without RETRY_REMINDERS cannot retry or select`() = runTest {
        val repository = FakeReminderRepository(listOf(reminder("1")), null)
        val component = ReminderQueueComponent(
            componentContext = DefaultComponentContext(
                lifecycle = LifecycleRegistry().also {
                    it.resume()
                },
            ),
            repository = repository,
            scheduler = FakeScheduler(),
            sender = FakeSender(),
            auth = FakeAuth(mayRetry = false),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onCloseClicked = {},
            onOpenMemberClicked = {},
        )

        component.state.test {
            var seen = awaitItem()
            repeat(RETRY_EMISSIONS) {
                if (!seen.mayRetry && seen.visibleReminders.isNotEmpty()) return@repeat
                seen = awaitItem()
            }
            assertFalse(seen.mayRetry)
            // Bulk selection is withheld too: there is no action it could lead to.
            assertFalse(seen.supportsSelection)

            component.onRetry(ReminderId("1"))
            assertEquals(
                emptyList(),
                repository.lastRetryIds,
                "the retry must not reach the repository",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `building the queue reports how many were queued`() = runTest {
        val scheduler = FakeScheduler(AppResult.Success(ScheduleOutcome(queued = 2)))
        val component = component(reminders = emptyList(), scheduler = scheduler)

        // Subscribing matters: state is `stateIn(WhileSubscribed)`, so the permission that gates
        // the build is not in `state.value` until something collects. The screen always does.
        component.state.test {
            awaitSettled { it.mayBuildQueue }

            component.onBuildQueue()

            assertEquals(1, scheduler.builds)
            assertEquals(
                QueueNotice.QueueBuilt(queued = 2),
                awaitSettled {
                    it.notice != null
                }.notice,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Queuing nothing is a normal outcome -- consent defaults to false -- so the notice has to
     * carry the reasons rather than a bare zero the user would read as a broken button.
     */
    @Test
    fun `queuing nothing explains why`() = runTest {
        val scheduler = FakeScheduler(
            AppResult.Success(
                ScheduleOutcome(queued = 0, skippedNoConsent = 3, skippedNoPhone = 1),
            ),
        )
        val component = component(reminders = emptyList(), scheduler = scheduler)

        component.state.test {
            awaitSettled { it.mayBuildQueue }

            component.onBuildQueue()

            assertEquals(
                QueueNotice.QueueBuiltNothing(noConsent = 3, noPhone = 1, alreadyQueued = 0),
                awaitSettled { it.notice != null }.notice,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The boundary, not the button. A component method is callable from anywhere, so hiding the
     * action in the UI is not what stops a role without the permission from writing reminders.
     */
    @Test
    fun `a role that may not change the queue cannot build it`() = runTest {
        val scheduler = FakeScheduler()
        val component = component(
            reminders = emptyList(),
            mayRetry = false,
            scheduler = scheduler,
        )
        component.state.test {
            // Drain until the session has been read, so the refusal is the permission's doing
            // rather than the initial value's.
            awaitSettled { !it.mayBuildQueue && it.content !is ReminderQueueContent.Loading }

            component.onBuildQueue()

            assertEquals(0, scheduler.builds, "the build must not reach the scheduler")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `running the queue reports what was sent`() = runTest {
        val sender = FakeSender(outcome = AppResult.Success(SendRunOutcome(sent = 3, failed = 1)))
        val component = component(reminders = emptyList(), sender = sender)

        component.state.test {
            awaitSettled { it.maySend && it.gatewayConnected }

            component.onRunQueue()

            assertEquals(1, sender.runs)
            assertEquals(
                QueueNotice.RunFinished(sent = 3, failed = 1),
                awaitSettled { it.notice != null }.notice,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Stopping early is its own notice: the remainder are still queued, not lost. */
    @Test
    fun `a rate-limited run says it stopped early`() = runTest {
        val sender = FakeSender(
            outcome = AppResult.Success(SendRunOutcome(sent = 2, failed = 1, stoppedEarly = true)),
        )
        val component = component(reminders = emptyList(), sender = sender)

        component.state.test {
            awaitSettled { it.maySend && it.gatewayConnected }

            component.onRunQueue()

            assertEquals(
                QueueNotice.RunStoppedEarly(sent = 2),
                awaitSettled { it.notice != null }.notice,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The guard that protects the queue before WhatsApp exists. Running against no gateway would
     * fail every row and spend its attempts, so the component must refuse even if something calls
     * the method directly.
     */
    @Test
    fun `the queue cannot be run with no gateway connected`() = runTest {
        val sender = FakeSender(isConfigured = false)
        val component = component(reminders = emptyList(), sender = sender)

        component.state.test {
            awaitSettled { it.maySend && !it.gatewayConnected }

            component.onRunQueue()

            assertEquals(0, sender.runs, "the run must not reach the sender")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.component(
        reminders: List<com.anfas.core.model.Reminder>,
        forced: AppResult<List<com.anfas.core.model.Reminder>>? = null,
        mayRetry: Boolean = true,
        scheduler: FakeScheduler = FakeScheduler(),
        sender: FakeSender = FakeSender(),
    ): ReminderQueueComponent {
        val lifecycle = LifecycleRegistry()
        val component = ReminderQueueComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = FakeReminderRepository(reminders, forced),
            scheduler = scheduler,
            sender = sender,
            auth = FakeAuth(mayRetry = mayRetry),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onCloseClicked = {},
            onOpenMemberClicked = {},
        )
        lifecycle.resume()
        return component
    }
}

/**
 * Records whether a build was asked for, and what to answer with.
 *
 * The scheduler's own rules are covered by `ReminderSchedulerTest` in `:core:data`; what matters
 * here is only that the component asks -- or, for a role without the permission, does not.
 */

/**
 * Drains emissions until [predicate] holds.
 *
 * The state is a `combine` of four sources, so one change surfaces as several frames -- and it is
 * `stateIn(WhileSubscribed)`, so nothing at all is populated until something collects. Both are why
 * these tests subscribe before acting rather than reading `state.value`.
 */
private suspend fun TurbineTestContext<ReminderQueueState>.awaitSettled(
    predicate: (ReminderQueueState) -> Boolean,
): ReminderQueueState {
    repeat(SETTLE_EMISSIONS) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("state never settled to the expected shape")
}

private const val SETTLE_EMISSIONS = 12

private class FakeScheduler(
    private val outcome: AppResult<ScheduleOutcome> =
        AppResult.Success(ScheduleOutcome(queued = 2)),
) : ReminderScheduler {
    var builds = 0
        private set

    override suspend fun buildQueue(): AppResult<ScheduleOutcome> {
        builds++
        return outcome
    }
}

/**
 * The send path itself is covered by `ReminderSenderTest` in `:core:data`; what matters here is
 * whether the component asks, and whether it refuses when there is no gateway or no permission.
 */
private class FakeSender(
    override val isConfigured: Boolean = true,
    private val outcome: AppResult<SendRunOutcome> = AppResult.Success(SendRunOutcome(sent = 3)),
) : ReminderSender {
    var runs = 0
        private set

    override suspend fun runQueue(): AppResult<SendRunOutcome> {
        runs++
        return outcome
    }
}
