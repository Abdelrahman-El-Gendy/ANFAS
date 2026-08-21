package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.model.FailureReason
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineFirstReminderRepositoryTest {

    @Test
    fun `retry requeues a rate limited failure`() = runTest {
        val dao = FakeReminderDao(listOf(reminderEntity("1", failureReason = "RATE_LIMITED")))
        val repo = OfflineFirstReminderRepository(dao)

        val result = assertIs<AppResult.Success<Int>>(repo.retry(listOf(ReminderId("1"))))

        assertEquals(1, result.value)
        val row = dao.current.single()
        assertEquals("QUEUED", row.status)
        assertEquals(2, row.attempts)
        // Requeueing must clear the failure, or a QUEUED row would still carry a reason.
        assertNull(row.failureReason)
        assertNull(row.failureProviderCode)
    }

    @Test
    fun `retry refuses a failure that needs human action`() = runTest {
        val dao = FakeReminderDao(listOf(reminderEntity("1", failureReason = "NOT_OPTED_IN")))
        val repo = OfflineFirstReminderRepository(dao)

        val result = assertIs<AppResult.Success<Int>>(repo.retry(listOf(ReminderId("1"))))

        assertEquals(0, result.value)
        // Untouched: still failed, attempts not burned.
        assertEquals("FAILED", dao.current.single().status)
        assertEquals(1, dao.current.single().attempts)
    }

    @Test
    fun `a mixed bulk retry requeues only the retryable ones`() = runTest {
        val dao = FakeReminderDao(
            listOf(
                reminderEntity("1", failureReason = "RATE_LIMITED"),
                reminderEntity("2", failureReason = "NOT_OPTED_IN"),
                reminderEntity("3", failureReason = "INVALID_PHONE_NUMBER"),
                reminderEntity("4", failureReason = "TEMPLATE_PAUSED"),
            ),
        )
        val repo = OfflineFirstReminderRepository(dao)

        val result = assertIs<AppResult.Success<Int>>(
            repo.retry(listOf("1", "2", "3", "4").map(::ReminderId)),
        )

        assertEquals(1, result.value)
        assertEquals("QUEUED", dao.current.first { it.id == "1" }.status)
        assertTrue(dao.current.filter { it.id != "1" }.all { it.status == "FAILED" })
    }

    @Test
    fun `retrying a message that is already queued does nothing`() = runTest {
        val dao = FakeReminderDao(
            listOf(reminderEntity("1", status = "QUEUED", failureReason = null)),
        )
        val repo = OfflineFirstReminderRepository(dao)

        assertEquals(0, assertIs<AppResult.Success<Int>>(repo.retry(listOf(ReminderId("1")))).value)
        assertEquals(1, dao.current.single().attempts)
    }

    @Test
    fun `an empty retry is a no-op rather than an error`() = runTest {
        val repo = OfflineFirstReminderRepository(FakeReminderDao())
        assertEquals(0, assertIs<AppResult.Success<Int>>(repo.retry(emptyList())).value)
    }

    @Test
    fun `counts are grouped by status with absent statuses reported as zero`() = runTest {
        val dao = FakeReminderDao(
            listOf(
                reminderEntity("1", status = "FAILED"),
                reminderEntity("2", status = "FAILED"),
                reminderEntity("3", status = "QUEUED", failureReason = null),
            ),
        )
        val repo = OfflineFirstReminderRepository(dao)

        repo.observeCounts().test {
            val counts = assertIs<AppResult.Success<ReminderCounts>>(awaitItem()).value
            assertEquals(2, counts.failed)
            assertEquals(1, counts.queued)
            assertEquals(0, counts.sent)
        }
    }

    @Test
    fun `the queue filters by status and template together`() = runTest {
        val dao = FakeReminderDao(
            listOf(
                reminderEntity("1", status = "FAILED", template = "REMINDER_AR"),
                reminderEntity("2", status = "FAILED", template = "PAYMENT_DUE"),
                reminderEntity("3", status = "QUEUED", template = "REMINDER_AR", failureReason = null),
            ),
        )
        val repo = OfflineFirstReminderRepository(dao)

        repo.observeQueue(ReminderStatus.FAILED, template = ReminderTemplate.REMINDER_AR).test {
            val loaded = assertIs<AppResult.Success<List<Reminder>>>(awaitItem()).value
            assertEquals(listOf("1"), loaded.map { it.id.value })
        }
    }

    @Test
    fun `search matches phone as well as name`() = runTest {
        val dao = FakeReminderDao(
            listOf(
                reminderEntity("1", name = "Omar Khaled", phone = "+20 100 123 4567"),
                reminderEntity("2", name = "Sara Ahmed", phone = "+20 111 222 3333"),
            ),
        )
        val repo = OfflineFirstReminderRepository(dao)

        repo.observeQueue(ReminderStatus.FAILED, query = "111 222").test {
            val loaded = assertIs<AppResult.Success<List<Reminder>>>(awaitItem()).value
            assertEquals(listOf("Sara Ahmed"), loaded.map { it.memberName })
        }
    }

    @Test
    fun `an unknown failure reason degrades to UNKNOWN and stays retryable`() = runTest {
        val dao = FakeReminderDao(listOf(reminderEntity("1", failureReason = "SOMETHING_NEW")))
        val repo = OfflineFirstReminderRepository(dao)

        repo.observeQueue(ReminderStatus.FAILED).test {
            val loaded = assertIs<AppResult.Success<List<Reminder>>>(awaitItem()).value
            assertEquals(FailureReason.UNKNOWN, loaded.single().failure?.reason)
            assertTrue(loaded.single().canRetry)
        }
    }

    @Test
    fun `a storage failure becomes AppError Storage rather than an exception`() = runTest {
        val dao = FakeReminderDao(listOf(reminderEntity("1")))
        dao.failure = IllegalStateException("database is locked")
        val repo = OfflineFirstReminderRepository(dao)

        repo.observeQueue(ReminderStatus.FAILED).test {
            val failure = assertIs<AppResult.Failure>(awaitItem())
            assertIs<AppError.Storage>(failure.error)
            assertTrue(failure.error.message.contains("database is locked"))
            awaitComplete()
        }
    }
}
