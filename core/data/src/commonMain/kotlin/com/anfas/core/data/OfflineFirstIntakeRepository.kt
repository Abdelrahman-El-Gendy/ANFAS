package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.database.IntakeDao
import com.anfas.core.database.MemberDao
import com.anfas.core.database.SyncTables
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeRow
import com.anfas.core.model.IntakeRowId
import com.anfas.core.model.IntakeValidator
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipNumbers
import com.anfas.core.model.MembershipStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * Room-backed OCR intake.
 *
 * Every read joins three sources — the batch, its rows, and the phone numbers already on the
 * books — and runs [IntakeValidator] over the result. That is deliberately not cached: the
 * membership changes independently of the sheet, so "ready to import" has to be recomputed at
 * read time or it will eventually be wrong in the one direction that matters, letting a
 * duplicate through.
 *
 * [newId] and [clock] are injected so imports are deterministic under test.
 */
internal class OfflineFirstIntakeRepository(
    private val intakeDao: IntakeDao,
    private val memberDao: MemberDao,
    private val newId: () -> String,
    private val clock: Clock = Clock.System,
) : IntakeRepository {

    override fun observeBatches(): Flow<AppResult<List<IntakeBatch>>> = intakeDao.observeBatches()
        .asAppResult("Could not load scanned sheets") { batches ->
            // Summaries only — rows are loaded when a sheet is opened. A list screen does
            // not need eight rows per batch, and validating all of them would be wasteful.
            batches.map { it.toDomain(rows = emptyList()) }
        }

    override fun observeBatch(id: IntakeBatchId): Flow<AppResult<IntakeBatch?>> = combine(
        intakeDao.observeBatch(id.value),
        intakeDao.observeRows(id.value),
        memberDao.observeNormalisedPhones(),
    ) { batch, rows, existingPhones ->
        batch?.toDomain(
            rows = IntakeValidator.validate(
                rows = rows.map { it.toDomain() },
                existingPhones = existingPhones.toSet(),
            ),
        )
    }.asAppResult("Could not load sheet ${id.value}") { it }

    override suspend fun createBatch(batch: IntakeBatch): AppResult<Unit> =
        runStorage("Could not save the scanned sheet") {
            val at = capturedAt()
            intakeDao.upsertBatchWithRowsTracked(
                batch = batch.toEntity(),
                rows = batch.rows.map { it.toEntity(batch.id) },
                changes = buildList {
                    add(changeFor(SyncTables.INTAKE_BATCHES, batch.id.value, at))
                    addAll(changesFor(SyncTables.INTAKE_ROWS, batch.rows.map { it.id.value }, at))
                },
            )
        }

    override suspend fun editField(
        rowId: IntakeRowId,
        field: IntakeFieldKey,
        value: String,
    ): AppResult<Unit> = runStorage("Could not save the correction") {
        val row = intakeDao.rowOnce(rowId.value)
            ?: throw IllegalStateException("No intake row ${rowId.value}")
        val edited = row.toDomain().editField(field, value)
        intakeDao.upsertRowsTracked(
            rows = listOf(edited.toEntity(IntakeBatchId(row.batchId))),
            changes = listOf(changeFor(SyncTables.INTAKE_ROWS, rowId.value)),
        )
    }

    override suspend fun importBatch(id: IntakeBatchId): AppResult<ImportOutcome> =
        runStorage("Could not import the sheet") {
            val batch = observeBatch(id).first().let { result ->
                when (result) {
                    is AppResult.Failure -> throw IllegalStateException(result.error.message)
                    is AppResult.Success -> result.value
                }
            } ?: throw IllegalStateException("No sheet ${id.value}")

            if (batch.status != IntakeBatchStatus.REVIEWING) {
                throw IllegalStateException("Sheet ${id.value} has already been ${batch.status}")
            }

            val importable = batch.importableRows
            val at = capturedAt()
            var created = emptyList<Member>()
            if (importable.isNotEmpty()) {
                // The whole run is allocated in one pass, so two rows cannot be handed the same
                // number -- which happened once, and Room's upsert silently collapsed eight
                // members into one. Shared with the manual add form: see MembershipNumbers.
                val issued = memberDao.observeAll().first().map { it.membershipNumber }
                val numbers = MembershipNumbers.nextRun(issued, count = importable.size)
                created = importable.mapIndexed { index, row ->
                    row.toMember(id = newId(), membershipNumber = numbers[index])
                }
            }
            // The members and the batch's new status go in one transaction. They were two calls
            // through two DAOs, so a failure between them left members created from a sheet still
            // marked REVIEWING -- and importing it again registered every one of them a second
            // time under fresh membership numbers.
            intakeDao.importTracked(
                batchId = id.value,
                status = IntakeBatchStatus.IMPORTED.name,
                members = created.map { it.toEntity() },
                changes = buildList {
                    addAll(changesFor(SyncTables.MEMBERS, created.map { it.id.value }, at))
                    add(changeFor(SyncTables.INTAKE_BATCHES, id.value, at))
                },
            )

            ImportOutcome(imported = importable.size, skipped = batch.blockedRows.size)
        }

    override suspend fun discardBatch(id: IntakeBatchId): AppResult<Unit> =
        runStorage("Could not discard the sheet") {
            intakeDao.setStatusTracked(
                id = id.value,
                status = IntakeBatchStatus.DISCARDED.name,
                change = changeFor(SyncTables.INTAKE_BATCHES, id.value),
            )
        }

    override suspend fun sourceImageUris(): AppResult<List<String>> =
        runStorage("Could not read the captured sheets") { intakeDao.sourceImageUris() }

    override suspend fun relocateSourceImage(from: String, to: String): AppResult<Unit> =
        runStorage("Could not repoint a captured sheet") {
            intakeDao.relocateSourceImage(from = from, to = to)
        }

    private fun IntakeRow.toMember(id: String, membershipNumber: String) = Member(
        id = MemberId(id),
        fullName = name.value.trim(),
        membershipNumber = membershipNumber,
        phone = phone.value.trim().takeIf { it.isNotEmpty() },
        // Imported members start ACTIVE: they have just handed over a signed sheet. The dates
        // on that sheet are validated and shown but not turned into a subscription term — see
        // the note on IntakeRepository.importBatch.
        status = MembershipStatus.ACTIVE,
        lastCheckInAt = null,
        avatarUrl = null,
    )
}

private fun IntakeRow.editField(field: IntakeFieldKey, value: String): IntakeRow = when (field) {
    IntakeFieldKey.NAME -> copy(name = name.editedTo(value))
    IntakeFieldKey.PHONE -> copy(phone = phone.editedTo(value))
    IntakeFieldKey.START_DATE -> copy(startDate = startDate.editedTo(value))
    IntakeFieldKey.END_DATE -> copy(endDate = endDate.editedTo(value))
    IntakeFieldKey.PLAN -> copy(plan = plan.editedTo(value))
}
