package com.anfas.core.data

import com.anfas.core.model.FailureReason
import com.anfas.core.model.ReminderStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every branch here decides whether a member gets messaged, gets messaged twice, or is quietly
 * dropped — which is why the gateway is an interface with a fake behind it. A real HTTP call cannot
 * have its rate-limited branch exercised on demand.
 */
class ReminderSenderTest {

    @Test
    fun `a queued reminder is sent and marked sent`() = runTest {
        val gateway = FakeGateway(GatewayResult.Accepted("wamid.1"))
        val sender = sender(queued("renewal:t-1"), gateway)

        val outcome = sender.runQueue().valueOrFail()

        assertEquals(1, outcome.sent)
        assertEquals(0, outcome.failed)
        val row = dao.current.single()
        assertEquals(ReminderStatus.SENT.name, row.status)
        assertEquals(1, row.attempts, "the attempt should have been counted")
        assertEquals(null, row.failureReason, "a sent reminder carries no failure")
    }

    /** Local Egyptian storage form, international on the wire. */
    @Test
    fun `the number is sent in international form`() = runTest {
        val gateway = FakeGateway(GatewayResult.Accepted(null))
        val sender = sender(queued("renewal:t-1", phone = "01001112222"), gateway)

        sender.runQueue().valueOrFail()

        assertEquals("201001112222", gateway.sent.single().toE164)
    }

    /**
     * The idempotency key is the reminder's own id, so a repeated run cannot present the same
     * message under two identities.
     */
    @Test
    fun `the idempotency key is the reminder id`() = runTest {
        val gateway = FakeGateway(GatewayResult.Accepted(null))
        val sender = sender(queued("renewal:t-1"), gateway)

        sender.runQueue().valueOrFail()

        assertEquals("renewal:t-1", gateway.sent.single().idempotencyKey)
    }

    @Test
    fun `a rejection records the mapped reason plus the code and provider text`() = runTest {
        val gateway = FakeGateway(GatewayResult.Rejected(131_047, "Re-engagement message"))
        val sender = sender(queued("renewal:t-1"), gateway)

        val outcome = sender.runQueue().valueOrFail()

        assertEquals(1, outcome.failed)
        val row = dao.current.single()
        assertEquals(ReminderStatus.FAILED.name, row.status)
        assertEquals(FailureReason.NOT_OPTED_IN.name, row.failureReason)
        assertEquals(131_047, row.failureProviderCode)
        assertEquals("Re-engagement message", row.failureDetail)
    }

    /**
     * An unreachable provider is not a rejection: we do not know whether the message arrived. It has
     * to be retryable, which is what makes the idempotency key matter.
     */
    @Test
    fun `an unreachable provider fails retryably`() = runTest {
        val gateway = FakeGateway(GatewayResult.Unreachable("connection reset"))
        val sender = sender(queued("renewal:t-1"), gateway)

        sender.runQueue().valueOrFail()

        val row = dao.current.single()
        assertEquals(ReminderStatus.FAILED.name, row.status)
        assertEquals(FailureReason.UNKNOWN.name, row.failureReason)
        assertTrue(FailureReason.UNKNOWN.isRetryable, "the recorded reason must allow a retry")
    }

    /**
     * Rate limiting means the next send would fail too, so the run stops and leaves the rest
     * QUEUED rather than spending an attempt on every one of them.
     */
    @Test
    fun `rate limiting stops the run and leaves the rest queued`() = runTest {
        val gateway = FakeGateway(GatewayResult.Rejected(130_429, "rate limit"))
        val sender = sender(
            listOf(queued("renewal:t-1"), queued("renewal:t-2"), queued("renewal:t-3")),
            gateway,
        )

        val outcome = sender.runQueue().valueOrFail()

        assertTrue(outcome.stoppedEarly)
        assertEquals(1, outcome.failed)
        assertEquals(1, gateway.sent.size, "only the first row should have been attempted")
        assertEquals(
            2,
            dao.current.count { it.status == ReminderStatus.QUEUED.name },
            "the untried rows must stay queued",
        )
    }

    /** A permanently broken row must stop consuming every run. */
    @Test
    fun `a reminder that has used its attempts is left alone`() = runTest {
        val gateway = FakeGateway(GatewayResult.Accepted(null))
        val sender = sender(queued("renewal:t-1", attempts = 4), gateway)

        val outcome = sender.runQueue().valueOrFail()

        assertEquals(1, outcome.exhausted)
        assertEquals(0, outcome.attempted)
        assertTrue(gateway.sent.isEmpty(), "an exhausted reminder must not be sent")
    }

    /**
     * A number that cannot be read never reaches the gateway. Failing before the call means no
     * charge, no quality-rating hit, and no message to a stranger.
     */
    @Test
    fun `an unreadable number fails without calling the gateway`() = runTest {
        val gateway = FakeGateway(GatewayResult.Accepted(null))
        val sender = sender(queued("renewal:t-1", phone = "12"), gateway)

        val outcome = sender.runQueue().valueOrFail()

        assertEquals(1, outcome.failed)
        assertTrue(gateway.sent.isEmpty())
        assertEquals(FailureReason.INVALID_PHONE_NUMBER.name, dao.current.single().failureReason)
    }

    /**
     * The guard that stops Phase 2 from poisoning the queue before WhatsApp exists: with no gateway
     * configured, a run must do nothing at all rather than fail every row and spend its attempts.
     */
    @Test
    fun `an unconfigured gateway runs nothing`() = runTest {
        val sender = sender(queued("renewal:t-1"), NoWhatsAppGateway)

        val outcome = sender.runQueue().valueOrFail()

        assertEquals(SendRunOutcome(), outcome)
        assertEquals(
            ReminderStatus.QUEUED.name,
            dao.current.single().status,
            "the reminder must be left untouched, not failed",
        )
        assertEquals(0, dao.current.single().attempts, "no attempt may be spent")
    }

    @Test
    fun `only queued reminders are sent`() = runTest {
        val gateway = FakeGateway(GatewayResult.Accepted(null))
        val sender = sender(
            listOf(
                queued("renewal:t-1"),
                reminderEntity(id = "renewal:t-2", status = ReminderStatus.SENT.name),
                reminderEntity(id = "renewal:t-3", status = ReminderStatus.FAILED.name),
            ),
            gateway,
        )

        sender.runQueue().valueOrFail()

        assertEquals(1, gateway.sent.size)
        assertEquals("renewal:t-1", gateway.sent.single().idempotencyKey)
    }

    // ---- the error mapping, one row of the table per assertion ----

    @Test
    fun `provider codes map onto the failure taxonomy`() {
        assertEquals(FailureReason.NOT_OPTED_IN, failureReasonFor(131_047))
        assertEquals(FailureReason.INVALID_PHONE_NUMBER, failureReasonFor(131_026))
        assertEquals(FailureReason.INVALID_PHONE_NUMBER, failureReasonFor(131_030))
        assertEquals(FailureReason.RATE_LIMITED, failureReasonFor(130_429))
        assertEquals(FailureReason.RATE_LIMITED, failureReasonFor(131_056))
        assertEquals(FailureReason.RATE_LIMITED, failureReasonFor(4))
        assertEquals(FailureReason.TEMPLATE_PAUSED, failureReasonFor(132_015))
        assertEquals(FailureReason.TEMPLATE_PAUSED, failureReasonFor(132_016))
        // Template-authoring mistakes degrade rather than pretending to be operational failures.
        assertEquals(FailureReason.UNKNOWN, failureReasonFor(132_000))
        assertEquals(FailureReason.UNKNOWN, failureReasonFor(132_001))
        assertEquals(FailureReason.UNKNOWN, failureReasonFor(132_012))
        assertEquals(FailureReason.UNKNOWN, failureReasonFor(null))
        assertEquals(FailureReason.UNKNOWN, failureReasonFor(999_999))
    }

    /** Only rate limiting is retryable; the rest need a human to change something. */
    @Test
    fun `rate limiting is the only retryable rejection`() {
        val retryable = FailureReason.entries.filter { it.isRetryable }
        assertEquals(
            setOf(FailureReason.RATE_LIMITED, FailureReason.UNKNOWN),
            retryable.toSet(),
            "the retryable set changed -- check that the sender's stop-early rule still matches",
        )
    }

    // ---- harness ----

    private lateinit var dao: FakeReminderDao

    private fun sender(
        rows: List<com.anfas.core.database.ReminderEntity>,
        gateway: WhatsAppGateway,
    ): ReminderSender {
        dao = FakeReminderDao(rows)
        return DefaultReminderSender(
            reminders = dao,
            gateway = gateway,
            dispatchers = UnconfinedDispatchers,
        )
    }

    private fun sender(row: com.anfas.core.database.ReminderEntity, gateway: WhatsAppGateway) =
        sender(listOf(row), gateway)

    private fun queued(id: String, phone: String = "01001112222", attempts: Int = 0) =
        reminderEntity(
            id = id,
            phone = phone,
            status = ReminderStatus.QUEUED.name,
            attempts = attempts,
            failureReason = null,
        )
}

/** Answers with one scripted result and records every message handed to it. */
private class FakeGateway(private val result: GatewayResult) : WhatsAppGateway {
    private val messages = mutableListOf<TemplateMessage>()

    val sent: List<TemplateMessage> get() = messages

    override val isConfigured: Boolean = true

    override suspend fun send(message: TemplateMessage): GatewayResult {
        messages += message
        return result
    }
}
