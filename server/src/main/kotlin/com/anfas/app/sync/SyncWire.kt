package com.anfas.app.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * The Stage 2 wire contract.
 *
 * Every field carries an explicit @SerialName, for the reason navigation Configs do: R8 renames
 * properties, and a wire format that shifts with R8's numbering is a production-only bug. These
 * types live in :server and not in :core:model on purpose — see "Sync - Stage 2" in CLAUDE.md.
 * The client half will declare its own mirror of them; ProtocolContractTest pins the JSON so the
 * two cannot drift unnoticed.
 */

@Serializable
data class RegisterDeviceRequest(
    /** Free text a human reads in the admin's device list, e.g. "Front desk iPad". */
    @SerialName("label") val label: String,
)

@Serializable
data class RegisterDeviceResponse(
    @SerialName("device_id") val deviceId: String,
    /** Shown exactly once. The server keeps only its hash and cannot show it again. */
    @SerialName("token") val token: String,
)

@Serializable
data class PushEntry(
    /** The device's own `sync_outbox.seq`. Strictly increasing within a request. */
    @SerialName("client_seq") val clientSeq: Long,
    @SerialName("table") val table: String,
    @SerialName("row_id") val rowId: String,
    /** `UPSERT` or `DELETE`, the names of `SyncOp`. */
    @SerialName("op") val op: String,
    /** The row as JSON for an UPSERT; absent for a DELETE. Stored, never interpreted. */
    @SerialName("payload") val payload: JsonObject? = null,
    @SerialName("captured_at_epoch_ms") val capturedAtEpochMs: Long,
)

@Serializable
data class PushRequest(@SerialName("entries") val entries: List<PushEntry>)

@Serializable
data class PushResponse(
    /**
     * The highest `client_seq` in the request. Everything up to it is durable on the box, whether
     * it was newly written or had already arrived on an earlier attempt, so the device may prune
     * its outbox through this value.
     */
    @SerialName("acknowledged_through") val acknowledgedThrough: Long,
    @SerialName("accepted") val accepted: Int,
    @SerialName("duplicates") val duplicates: Int,
)

@Serializable
data class StoredChange(
    @SerialName("server_seq") val serverSeq: Long,
    @SerialName("device_id") val deviceId: String,
    @SerialName("client_seq") val clientSeq: Long,
    @SerialName("table") val table: String,
    @SerialName("row_id") val rowId: String,
    @SerialName("op") val op: String,
    @SerialName("payload") val payload: JsonObject? = null,
    @SerialName("captured_at_epoch_ms") val capturedAtEpochMs: Long,
)

@Serializable
data class ExportPage(
    @SerialName("entries") val entries: List<StoredChange>,
    /** Pass as `after_seq` to continue. Equal to the request's own value when the page is empty. */
    @SerialName("next_after_seq") val nextAfterSeq: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
data class ErrorBody(@SerialName("error") val error: String)

enum class PushOp { UPSERT, DELETE }

/**
 * The tables the box accepts, mirroring `SyncTables` in :core:database.
 *
 * It cannot import that — :server sees :core:model only — so the list is restated, and
 * ProtocolContractTest pins it. What is absent is as deliberate as what is present: `reminders`
 * (a re-runner would resurrect a SENT row as QUEUED and send a duplicate renewal notice), `staff`
 * (password verifiers must not be put on a wire) and the sync bookkeeping tables themselves.
 * Refusing them here rather than trusting the client makes "never synced" a property of the box.
 */
object SyncedTables {
    val ALL: Set<String> = setOf(
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
    )
}
