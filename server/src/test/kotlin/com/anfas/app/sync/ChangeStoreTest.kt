package com.anfas.app.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun upsert(
    seq: Long,
    row: String = "m$seq",
    table: String = "members",
    name: String = "x",
) = PushEntry(
    clientSeq = seq,
    table = table,
    rowId = row,
    op = "UPSERT",
    payload = JsonObject(mapOf("name" to JsonPrimitive(name))),
    capturedAtEpochMs = 1_000L + seq,
)

private fun delete(seq: Long, row: String, table: String = "members") = PushEntry(
    clientSeq = seq,
    table = table,
    rowId = row,
    op = "DELETE",
    capturedAtEpochMs = 1_000L + seq,
)

class ChangeStoreTest {

    private fun memoryStore() = SqliteChangeStore("jdbc:sqlite::memory:")

    @Test
    fun registeredTokenAuthenticatesAndUnknownDoesNot() {
        val store = memoryStore()
        val device = store.registerDevice("Front desk")

        assertEquals(device.deviceId, store.authenticate(device.token))
        assertNull(store.authenticate("not-a-token"))
    }

    @Test
    fun revokedDeviceStopsAuthenticating() {
        val store = memoryStore()
        val device = store.registerDevice("Front desk")

        assertTrue(store.revokeDevice(device.deviceId))

        assertNull(store.authenticate(device.token))
    }

    @Test
    fun acceptedChangesGetIncreasingServerSeq() {
        val store = memoryStore()
        val d = store.registerDevice("a").deviceId

        store.append(d, listOf(upsert(1), upsert(2)))
        store.append(d, listOf(upsert(3)))

        val seqs = store.export(0, 100, latestOnly = false).map { it.serverSeq }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(3, seqs.distinct().size)
    }

    @Test
    fun replayedBatchIsAcknowledgedButStoredOnce() {
        val store = memoryStore()
        val d = store.registerDevice("a").deviceId
        val batch = listOf(upsert(1), upsert(2))

        val first = store.append(d, batch)
        val replay = store.append(d, batch)

        assertEquals(AppendResult.Accepted(accepted = 2, duplicates = 0, through = 2), first)
        assertEquals(AppendResult.Accepted(accepted = 0, duplicates = 2, through = 2), replay)
        assertEquals(2, store.export(0, 100, latestOnly = false).size)
    }

    @Test
    fun partiallyOverlappingRetryStoresOnlyTheNewTail() {
        val store = memoryStore()
        val d = store.registerDevice("a").deviceId
        store.append(d, listOf(upsert(1), upsert(2)))

        val result = store.append(d, listOf(upsert(2), upsert(3)))

        assertEquals(AppendResult.Accepted(accepted = 1, duplicates = 1, through = 3), result)
        assertEquals(3, store.export(0, 100, latestOnly = false).size)
    }

    @Test
    fun reusedClientSeqWithDifferentContentIsRefusedAndWritesNothing() {
        val store = memoryStore()
        val d = store.registerDevice("a").deviceId
        store.append(d, listOf(upsert(1, row = "m1")))

        // A restored outbox starts numbering again: seq 1 now describes a different row, and
        // seq 2 is genuinely new. Neither may be half-applied.
        val result = store.append(d, listOf(upsert(2, row = "m9"), upsert(1, row = "other")))

        // The validator forbids this order on the wire; the store must still hold its own line.
        assertIs<AppendResult.SequenceReused>(result)
        assertEquals(1, store.export(0, 100, latestOnly = false).size)
    }

    @Test
    fun sameClientSeqFromTwoDevicesIsNotADuplicate() {
        val store = memoryStore()
        val a = store.registerDevice("a").deviceId
        val b = store.registerDevice("b").deviceId

        store.append(a, listOf(upsert(1, row = "ma")))
        store.append(b, listOf(upsert(1, row = "mb")))

        assertEquals(2, store.export(0, 100, latestOnly = false).size)
    }

    @Test
    fun latestOnlyKeepsNewestChangePerRowAndDropsDeletedRows() {
        val store = memoryStore()
        val d = store.registerDevice("a").deviceId
        store.append(
            d,
            listOf(
                upsert(1, row = "m1", name = "old"),
                upsert(2, row = "m1", name = "new"),
                upsert(3, row = "m2"),
                delete(4, row = "m2"),
                upsert(5, row = "m3"),
            ),
        )

        val live = store.export(0, 100, latestOnly = true)

        assertEquals(listOf("m1", "m3"), live.map { it.rowId })
        assertEquals(JsonPrimitive("new"), live.first().payload!!["name"])
    }

    @Test
    fun rowIdsAreScopedByTable() {
        val store = memoryStore()
        val d = store.registerDevice("a").deviceId
        store.append(
            d,
            listOf(
                delete(1, row = "1", table = "members"),
                upsert(2, row = "1", table = "equipment"),
            ),
        )

        val live = store.export(0, 100, latestOnly = true)

        assertEquals(listOf("equipment"), live.map { it.table })
    }

    @Test
    fun changesAndDevicesSurviveReopeningTheFile() {
        val file = Files.createTempFile("anfas-server", ".db").also { it.toFile().deleteOnExit() }
        val url = "jdbc:sqlite:$file"

        val first = SqliteChangeStore(url)
        val device = first.registerDevice("a")
        first.append(device.deviceId, listOf(upsert(1)))
        first.close()

        val second = SqliteChangeStore(url)
        assertEquals(device.deviceId, second.authenticate(device.token))
        assertEquals(1, second.export(0, 10, latestOnly = false).size)
        // And the replay after a restart is still recognised, so a crash mid-ack loses nothing.
        assertEquals(
            AppendResult.Accepted(0, 1, 1),
            second.append(device.deviceId, listOf(upsert(1))),
        )
        second.close()
    }

    @Test
    fun tokenIsNotStoredInTheClear() {
        val file = Files.createTempFile("anfas-server", ".db").also { it.toFile().deleteOnExit() }
        val store = SqliteChangeStore("jdbc:sqlite:$file")
        val device = store.registerDevice("a")
        store.close()

        val raw = Files.readAllBytes(file).toString(Charsets.ISO_8859_1)
        assertTrue(device.token !in raw, "token must not appear in the database file")
        assertNotEquals("", device.token)
    }
}
