package com.anfas.app.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64
import java.util.UUID

data class RegisteredDevice(val deviceId: String, val token: String)

sealed interface AppendResult {
    data class Accepted(val accepted: Int, val duplicates: Int, val through: Long) : AppendResult

    /**
     * A `client_seq` this device already pushed arrived again describing a *different* change.
     * That is not a retry. It means the device's outbox numbering restarted — a restored backup, a
     * reinstall that kept its credential — and treating it as a duplicate would silently discard
     * a real change forever. The whole request is refused and nothing is written.
     */
    data class SequenceReused(val clientSeq: Long) : AppendResult
}

/**
 * The box's durable log of what devices pushed.
 *
 * An interface so routes are tested against the real thing ([SqliteChangeStore] on a temp file)
 * without a mock standing in for the one component whose entire job is not to lose data.
 */
interface ChangeStore {
    fun registerDevice(label: String): RegisteredDevice

    /** The device id a token belongs to, or null if unknown or revoked. */
    fun authenticate(token: String): String?

    fun revokeDevice(deviceId: String): Boolean

    fun append(deviceId: String, entries: List<PushEntry>): AppendResult

    /**
     * Raw log pages in server order, or — with [latestOnly] — the live state: the newest change
     * per row, kept only when that change is an UPSERT. A row whose newest change is a DELETE is
     * dropped, which is what makes a restore into an empty database come out right.
     */
    fun export(afterSeq: Long, limit: Int, latestOnly: Boolean): List<StoredChange>

    fun close()
}

/**
 * SQLite through plain JDBC, one connection, every call serialised on it.
 *
 * One connection is the honest size: the box serves a handful of devices pushing small batches,
 * and SQLite allows a single writer regardless. `synchronous=FULL` because this store exists to be
 * the durable copy — losing the last acknowledged transaction to a power cut on the mini-PC would
 * defeat the stage's only purpose.
 *
 * The server's change log is **append-only** and `server_seq` is `AUTOINCREMENT`, not `ROWID`
 * reuse: SQLite may reissue the highest rowid after its row is deleted, and a cursor that can be
 * handed out twice is exactly the failure the server-assigned sequence exists to prevent.
 */
class SqliteChangeStore(
    jdbcUrl: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ChangeStore {

    private val connection: Connection = DriverManager.getConnection(jdbcUrl)
    private val lock = Any()
    private val random = SecureRandom()
    private val json = Json

    init {
        synchronized(lock) {
            connection.createStatement().use { s ->
                s.execute("PRAGMA journal_mode=WAL")
                s.execute("PRAGMA synchronous=FULL")
                s.execute(
                    """
                    CREATE TABLE IF NOT EXISTS devices (
                        device_id TEXT PRIMARY KEY,
                        label TEXT NOT NULL,
                        token_hash TEXT NOT NULL UNIQUE,
                        registered_at INTEGER NOT NULL,
                        revoked INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                s.execute(
                    """
                    CREATE TABLE IF NOT EXISTS changes (
                        server_seq INTEGER PRIMARY KEY AUTOINCREMENT,
                        device_id TEXT NOT NULL,
                        client_seq INTEGER NOT NULL,
                        table_name TEXT NOT NULL,
                        row_id TEXT NOT NULL,
                        op TEXT NOT NULL,
                        payload TEXT,
                        captured_at INTEGER NOT NULL,
                        received_at INTEGER NOT NULL,
                        UNIQUE (device_id, client_seq)
                    )
                    """.trimIndent(),
                )
                s.execute(
                    "CREATE INDEX IF NOT EXISTS idx_changes_row ON changes (table_name, row_id, server_seq)",
                )
            }
        }
    }

    override fun registerDevice(label: String): RegisteredDevice = synchronized(lock) {
        val deviceId = UUID.randomUUID().toString()
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }
        connection.prepareStatement(
            "INSERT INTO devices (device_id, label, token_hash, registered_at) VALUES (?, ?, ?, ?)",
        ).use {
            it.setString(1, deviceId)
            it.setString(2, label)
            it.setString(3, hashToken(token))
            it.setLong(4, clock())
            it.executeUpdate()
        }
        RegisteredDevice(deviceId, token)
    }

    override fun authenticate(token: String): String? = synchronized(lock) {
        connection.prepareStatement(
            "SELECT device_id FROM devices WHERE token_hash = ? AND revoked = 0",
        )
            .use {
                it.setString(1, hashToken(token))
                it.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
    }

    override fun revokeDevice(deviceId: String): Boolean = synchronized(lock) {
        connection.prepareStatement("UPDATE devices SET revoked = 1 WHERE device_id = ?").use {
            it.setString(1, deviceId)
            it.executeUpdate() > 0
        }
    }

    override fun append(deviceId: String, entries: List<PushEntry>): AppendResult =
        synchronized(lock) {
            var accepted = 0
            var duplicates = 0
            connection.autoCommit = false
            try {
                for (e in entries) {
                    val existing = findExisting(deviceId, e.clientSeq)
                    if (existing == null) {
                        insert(deviceId, e)
                        accepted++
                    } else if (existing.matches(e)) {
                        duplicates++
                    } else {
                        connection.rollback()
                        return@synchronized AppendResult.SequenceReused(e.clientSeq)
                    }
                }
                connection.commit()
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            } finally {
                connection.autoCommit = true
            }
            AppendResult.Accepted(accepted, duplicates, entries.maxOf { it.clientSeq })
        }

    override fun export(afterSeq: Long, limit: Int, latestOnly: Boolean): List<StoredChange> =
        synchronized(lock) {
            val sql = if (latestOnly) {
                """
                SELECT c.* FROM changes c
                WHERE c.server_seq > ? AND c.op = 'UPSERT'
                  AND c.server_seq = (
                      SELECT MAX(l.server_seq) FROM changes l
                      WHERE l.table_name = c.table_name AND l.row_id = c.row_id
                  )
                ORDER BY c.server_seq ASC LIMIT ?
                """.trimIndent()
            } else {
                "SELECT * FROM changes WHERE server_seq > ? ORDER BY server_seq ASC LIMIT ?"
            }
            connection.prepareStatement(sql).use { ps ->
                ps.setLong(1, afterSeq)
                ps.setInt(2, limit)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                StoredChange(
                                    serverSeq = rs.getLong("server_seq"),
                                    deviceId = rs.getString("device_id"),
                                    clientSeq = rs.getLong("client_seq"),
                                    table = rs.getString("table_name"),
                                    rowId = rs.getString("row_id"),
                                    op = rs.getString("op"),
                                    payload = rs.getString("payload")
                                        ?.let { json.decodeFromString<JsonObject>(it) },
                                    capturedAtEpochMs = rs.getLong("captured_at"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun close() = synchronized(lock) { connection.close() }

    private data class Existing(val table: String, val rowId: String, val op: String)

    private fun Existing.matches(e: PushEntry) = table == e.table && rowId == e.rowId && op == e.op

    private fun findExisting(deviceId: String, clientSeq: Long): Existing? =
        connection.prepareStatement(
            "SELECT table_name, row_id, op FROM changes WHERE device_id = ? AND client_seq = ?",
        ).use {
            it.setString(1, deviceId)
            it.setLong(2, clientSeq)
            it.executeQuery().use { rs ->
                if (rs.next()) Existing(rs.getString(1), rs.getString(2), rs.getString(3)) else null
            }
        }

    private fun insert(deviceId: String, e: PushEntry) {
        connection.prepareStatement(
            "INSERT INTO changes (device_id, client_seq, table_name, row_id, op, payload, " +
                "captured_at, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        ).use {
            it.setString(1, deviceId)
            it.setLong(2, e.clientSeq)
            it.setString(3, e.table)
            it.setString(4, e.rowId)
            it.setString(5, e.op)
            it.setString(6, e.payload?.let(json::encodeToString))
            it.setLong(7, e.capturedAtEpochMs)
            it.setLong(8, clock())
            it.executeUpdate()
        }
    }

    private companion object {
        const val TOKEN_BYTES = 32

        /**
         * SHA-256, not a password hash. The token is 256 random bits, so there is nothing to
         * brute-force and a slow hash would only add latency to every request. Hashing at all
         * means a leaked database file does not hand out working credentials.
         */
        fun hashToken(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
