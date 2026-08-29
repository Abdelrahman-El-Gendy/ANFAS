package com.anfas.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query

/**
 * What this device has changed and not yet sent.
 *
 * There is no server, and this has no reader outside its tests until there is one — which in this
 * codebase is normally an argument against building something. It is built now for one reason: an
 * outbox records the only fact about a write that **cannot be reconstructed afterwards**. Once a
 * row has been edited, nothing on disk can tell you it changed; once a row has been deleted,
 * nothing can tell you it ever existed. Every other piece of sync metadata can be added later at
 * no loss.
 *
 * That principle also decides what is deliberately **not** here, against the first draft of
 * `design/sync-layer.md`:
 * - **No `updated_at` on the fourteen tables.** The design lists it, but nothing displays it and
 *   its value at the moment the column is added is simply "now" — perfectly recoverable. Twelve
 *   columns no screen reads is how scaffolding rots.
 * - **No `base_version` column here.** Optimistic concurrency needs the version a push claims to
 *   have changed *from*, and no server has ever assigned one, so the column could only ever be
 *   null. A column that can only be null is indistinguishable from a broken one. It arrives with
 *   the server that assigns versions, and Room's auto-migration makes adding a nullable column
 *   then nearly free.
 *
 * So v13 adds two tables and not one column to an existing table.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["table_name", "row_id"])],
)
data class SyncOutboxEntity(
    /**
     * Local ordering, and deliberately not a timestamp. `AUTOINCREMENT` is monotonic within this
     * device regardless of what the clock does — the same reason the protocol's own cursor is a
     * server-assigned sequence rather than a written time.
     */
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    val op: String,
    /**
     * Diagnostic, not protocol: it answers "how long has this been queued", which is worth having
     * at a desk. Nothing compares it across devices.
     */
    @ColumnInfo(name = "captured_at_epoch_ms") val capturedAtEpochMs: Long,
)

/**
 * That a row was deleted, kept after the row itself is gone.
 *
 * Separate from a `deleted` flag on each table, and that is the load-bearing choice rather than a
 * stylistic one. SQLite does not cascade an `UPDATE`, so flagging a member would leave
 * `therapy_cases` and `therapy_sessions` live and unflagged beneath a tombstoned parent —
 * invisibly, because nothing filters on the flag. It would also need `AND deleted = 0` in roughly
 * forty existing queries, where missing one makes a deleted member's phone permanently block
 * re-registering that person through intake, and it would delete `MemberProfileState.Missing`,
 * a state whose KDoc already anticipates sync.
 *
 * Deleting for real keeps SQLite's own CASCADE working. The cost is that the ids of the cascaded
 * children have to be read *before* the delete, which is what the `deleteTracked` methods do.
 *
 * Distinct from an outbox `DELETE` entry, which is drained and pruned once pushed: a tombstone
 * outlives that, so a later pull carrying a stale insert cannot resurrect the row.
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["table_name", "row_id"])
data class SyncTombstoneEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    @ColumnInfo(name = "deleted_at_epoch_ms") val deletedAtEpochMs: Long,
)

/** The two operations a push can carry. `op` is stored by name, as every other enum here is. */
enum class SyncOp { UPSERT, DELETE }

/**
 * Table names as the outbox records them.
 *
 * Constants rather than string literals at each call site: an entry filed under a misspelled table
 * is not a compile error and not a test failure anywhere but in the one test that names that
 * table — it is a row that pushes to nothing, forever.
 */
object SyncTables {
    const val MEMBERS = "members"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SUBSCRIPTION_PLANS = "subscription_plans"
    const val CHECK_INS = "check_ins"
    const val SCHEDULED_CLASSES = "scheduled_classes"
    const val EQUIPMENT = "equipment"
    const val MAINTENANCE_LOG = "maintenance_log"
    const val ANNOUNCEMENTS = "announcements"
    const val THERAPY_CASES = "therapy_cases"
    const val THERAPY_SESSIONS = "therapy_sessions"
    const val INTAKE_BATCHES = "intake_batches"
    const val INTAKE_ROWS = "intake_rows"
}

/**
 * Reads over the sync bookkeeping. Writes deliberately do **not** live here: an outbox entry must
 * land in the same transaction as the row it describes, so each write is a `@Transaction` method
 * on the DAO that owns that row. A free-standing `append` would compile, run, and silently allow
 * a change to be committed with no record of it.
 */
@Dao
interface SyncDao {

    @Query("SELECT * FROM sync_outbox ORDER BY seq ASC")
    suspend fun pendingChanges(): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_tombstones ORDER BY deleted_at_epoch_ms ASC")
    suspend fun tombstones(): List<SyncTombstoneEntity>

    /**
     * Present for the ingest path a later stage adds, and used now by the tests that prove a
     * remote delete can be recorded without a local row to delete.
     */
    @Insert
    suspend fun recordTombstones(entries: List<SyncTombstoneEntity>)
}
