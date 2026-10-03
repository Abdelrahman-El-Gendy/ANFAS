package com.anfas.app.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(
    seq: Long,
    table: String = "members",
    op: String = "UPSERT",
    payload: JsonObject? = JsonObject(emptyMap()),
) = PushEntry(seq, table, "r$seq", op, if (op == "UPSERT") payload else null, 1L)

/** The JSON field names are the contract; the Kotlin property names are not. */
class ProtocolContractTest {

    @Test
    fun pushEntryUsesSnakeCaseWireNames() {
        assertEquals(
            """{"client_seq":7,"table":"members","row_id":"r7","op":"UPSERT",""" +
                """"payload":{},"captured_at_epoch_ms":1}""",
            Json { encodeDefaults = true }.encodeToString(PushEntry.serializer(), entry(7)),
        )
    }

    @Test
    fun responseNamesArePinned() {
        val json = Json { encodeDefaults = true }
        assertEquals(
            """{"acknowledged_through":3,"accepted":2,"duplicates":1}""",
            json.encodeToString(PushResponse.serializer(), PushResponse(3, 2, 1)),
        )
        assertEquals(
            """{"device_id":"d","token":"t"}""",
            json.encodeToString(
                RegisterDeviceResponse.serializer(),
                RegisterDeviceResponse("d", "t"),
            ),
        )
    }

    @Test
    fun syncedTablesAreExactlyTheTwelveTheClientRecords() {
        assertEquals(
            setOf(
                "members",
                "subscriptions",
                "subscription_plans",
                "check_ins",
                "scheduled_classes",
                "equipment",
                "maintenance_log",
                "announcements",
                "therapy_cases",
                "therapy_sessions",
                "intake_batches",
                "intake_rows",
            ),
            SyncedTables.ALL,
        )
        for (never in listOf("reminders", "staff", "sync_outbox", "sync_tombstones")) {
            assertTrue(never !in SyncedTables.ALL, never)
        }
    }

    @Test
    fun validRequestPasses() {
        assertNull(validatePush(PushRequest(listOf(entry(1), entry(2, op = "DELETE")))))
    }

    @Test
    fun emptyOutOfOrderAndRepeatedSequencesAreRefused() {
        assertTrue(validatePush(PushRequest(emptyList()))!!.contains("empty"))
        assertTrue(validatePush(PushRequest(listOf(entry(2), entry(1))))!!.contains("increasing"))
        assertTrue(validatePush(PushRequest(listOf(entry(1), entry(1))))!!.contains("increasing"))
    }

    @Test
    fun upsertNeedsPayloadAndDeleteMustNotHaveOne() {
        val bare = PushEntry(1, "members", "r", "UPSERT", null, 1L)
        val loaded = PushEntry(1, "members", "r", "DELETE", JsonObject(emptyMap()), 1L)
        assertTrue(validatePush(PushRequest(listOf(bare)))!!.contains("payload"))
        assertTrue(validatePush(PushRequest(listOf(loaded)))!!.contains("payload"))
    }

    @Test
    fun unknownOpBlankRowIdAndOversizeAreRefused() {
        assertTrue(validatePush(PushRequest(listOf(entry(1).copy(op = "PATCH"))))!!.contains("op"))
        assertTrue(
            validatePush(PushRequest(listOf(entry(1).copy(rowId = " "))))!!.contains("row_id"),
        )
        val big = JsonObject(mapOf("x" to JsonPrimitive("a".repeat(PushLimits.MAX_PAYLOAD_CHARS))))
        assertTrue(validatePush(PushRequest(listOf(entry(1, payload = big))))!!.contains("payload"))
        val many = (1L..(PushLimits.MAX_ENTRIES + 1L)).map { entry(it) }
        assertTrue(validatePush(PushRequest(many))!!.contains("at most"))
    }

    @Test
    fun unsyncedTableIsNamedInTheRefusal() {
        assertTrue(validatePush(PushRequest(listOf(entry(1, table = "staff"))))!!.contains("staff"))
    }
}
