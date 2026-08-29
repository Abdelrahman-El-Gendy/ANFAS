package com.anfas.core.data

import com.anfas.core.database.SyncOp
import com.anfas.core.database.SyncOutboxEntity
import kotlin.time.Clock

/**
 * How a repository records that it changed something.
 *
 * These are one-liners on purpose. The value is not the code they save but that every write path
 * in `:core:data` files its outbox entry the same way — a hand-rolled entry with a mistyped table
 * name or the wrong op is not a compile error and not a test failure anywhere except in the single
 * test that names that table, and what it produces is a row that pushes to nothing, forever.
 *
 * [capturedAt] is read from the device clock, which is fine precisely because nothing compares it
 * across devices: local ordering comes from `sync_outbox.seq`, and the protocol's cursor is
 * server-assigned. See `design/sync-layer.md` §1 for why a written timestamp can never be either
 * of those things.
 */
internal fun changeFor(table: String, rowId: String, at: Long = capturedAt()): SyncOutboxEntity =
    SyncOutboxEntity(
        tableName = table,
        rowId = rowId,
        op = SyncOp.UPSERT.name,
        capturedAtEpochMs = at,
    )

internal fun changesFor(
    table: String,
    rowIds: List<String>,
    at: Long = capturedAt(),
): List<SyncOutboxEntity> = rowIds.map { changeFor(table, it, at) }

internal fun capturedAt(): Long = Clock.System.now().toEpochMilliseconds()
