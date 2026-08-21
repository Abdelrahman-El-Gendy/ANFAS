package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.database.IntakeDao
import com.anfas.core.database.MemberDao
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeRow
import com.anfas.core.model.IntakeRowId
import com.anfas.core.model.IntakeValidator
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
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
            intakeDao.upsertBatchWithRows(
                batch = batch.toEntity(),
                rows = batch.rows.map { it.toEntity(batch.id) },
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
        intakeDao.upsertRows(listOf(edited.toEntity(IntakeBatchId(row.batchId))))
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
            if (importable.isNotEmpty()) {
                var nextNumber = nextMembershipNumber()
                val members = importable.map { row ->
                    row.toMember(id = newId(), membershipNumber = formatNumber(nextNumber++))
                }
                memberDao.upsertAll(members.map { it.toEntity() })
            }
            intakeDao.setStatus(id.value, IntakeBatchStatus.IMPORTED.name)

            ImportOutcome(imported = importable.size, skipped = batch.blockedRows.size)
        }

    override suspend fun discardBatch(id: IntakeBatchId): AppResult<Unit> =
        runStorage("Could not discard the sheet") {
            intakeDao.setStatus(id.value, IntakeBatchStatus.DISCARDED.name)
        }

    /**
     * Next free membership number, one past the highest currently issued.
     *
     * This is a placeholder for a real numbering policy — the export shows numbers around
     * 88xxx with no stated scheme, and prefixes, check digits or per-branch ranges are a
     * business decision, not something to invent here.
     */
    private suspend fun nextMembershipNumber(): Int {
        val highest = memberDao.observeAll().first()
            .mapNotNull { it.membershipNumber.filter(Char::isDigit).toIntOrNull() }
            .maxOrNull()
        return (highest ?: FIRST_MEMBERSHIP_NUMBER - 1) + 1
    }

    private fun formatNumber(value: Int) = "#$value"

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

    private companion object {
        const val FIRST_MEMBERSHIP_NUMBER = 10_000
    }
}

private fun IntakeRow.editField(field: IntakeFieldKey, value: String): IntakeRow = when (field) {
    IntakeFieldKey.NAME -> copy(name = name.editedTo(value))
    IntakeFieldKey.PHONE -> copy(phone = phone.editedTo(value))
    IntakeFieldKey.START_DATE -> copy(startDate = startDate.editedTo(value))
    IntakeFieldKey.END_DATE -> copy(endDate = endDate.editedTo(value))
    IntakeFieldKey.PLAN -> copy(plan = plan.editedTo(value))
}
