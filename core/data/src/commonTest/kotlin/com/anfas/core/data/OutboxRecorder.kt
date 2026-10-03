package com.anfas.core.data

import com.anfas.core.database.SyncOp
import com.anfas.core.database.SyncOutboxEntity
import com.anfas.core.database.SyncTombstoneEntity

/**
 * What a fake DAO records instead of a `sync_outbox` table.
 *
 * The fakes get the real bookkeeping for free, which is the point: every `*Tracked` method is a
 * **default method on the DAO interface**, so a fake that implements only [SyncOutboxEntity]
 * capture runs the same sequencing production does — including reading cascade child ids before a
 * delete. A fake that re-implemented the tracked methods would be asserting against itself.
 *
 * It does not simulate a transaction, and cannot: these are plain in-memory lists. Atomicity is
 * the DAO's `@Transaction` annotation and is verified by the real Room instrumentation, not here.
 * What these tests prove is the thing that actually goes wrong in practice — that a write path
 * records *something*, under the right table and the right op.
 */
internal class OutboxRecorder {
    val changes = mutableListOf<SyncOutboxEntity>()
    val tombstones = mutableListOf<SyncTombstoneEntity>()

    fun record(entry: SyncOutboxEntity) {
        changes += entry
    }

    fun record(entries: List<SyncTombstoneEntity>) {
        tombstones += entries
    }

    /** `table to op` for every entry, in order — what nearly every assertion here wants. */
    val recorded: List<Pair<String, String>>
        get() = changes.map { it.tableName to it.op }

    fun upserts(table: String): List<String> =
        changes.filter { it.tableName == table && it.op == SyncOp.UPSERT.name }.map { it.rowId }

    fun deletes(table: String): List<String> =
        changes.filter { it.tableName == table && it.op == SyncOp.DELETE.name }.map { it.rowId }

    fun tombstoned(table: String): List<String> =
        tombstones.filter { it.tableName == table }.map { it.rowId }
}
