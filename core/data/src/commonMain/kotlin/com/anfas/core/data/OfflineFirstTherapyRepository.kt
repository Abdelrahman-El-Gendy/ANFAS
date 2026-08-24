package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.database.StaffDao
import com.anfas.core.database.TherapyCaseDao
import com.anfas.core.database.TherapyCaseEntity
import com.anfas.core.database.TherapySessionEntity
import com.anfas.core.model.CaseStatus
import com.anfas.core.model.MemberId
import com.anfas.core.model.TherapyCase
import com.anfas.core.model.TherapyCaseId
import com.anfas.core.model.TherapySession
import com.anfas.core.model.TherapySessionId
import com.anfas.core.model.TreatmentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineFirstTherapyRepository(
    private val cases: TherapyCaseDao,
    private val staff: StaffDao,
    private val dispatchers: AppDispatchers,
) : TherapyRepository {

    override fun observeLatestCase(memberId: MemberId): Flow<AppResult<TherapyCaseDetail?>> =
        cases.observeLatestForMember(memberId.value).flatMapLatest { caseEntity ->
            if (caseEntity == null) {
                flowOf(null)
            } else {
                combine(
                    cases.observeSessions(caseEntity.id),
                    staff.observeAll(),
                ) { sessionRows, staffRows ->
                    TherapyCaseDetail(
                        case = caseEntity.toDomain(),
                        sessions = sessionRows.map { it.toDomain() },
                        staffNames = staffRows.associate { it.id to it.displayName },
                    )
                }
            }
        }.asAppResult("Could not load the therapy record") { it }

    override suspend fun openCase(
        memberId: MemberId,
        condition: String,
        therapistStaffId: String?,
        referredBy: String?,
        onset: String,
        mechanism: String,
        contraindications: String?,
        openedOn: LocalDate,
    ): AppResult<SaveCaseOutcome> = withContext(dispatchers.io) {
        runStorage("Could not open the case") {
            val problems = validateCondition(condition)
            if (problems.isNotEmpty()) return@runStorage SaveCaseOutcome.Invalid(problems)

            val current = cases.findLatestForMember(memberId.value)
            if (current != null && current.status == CaseStatus.ACTIVE.name) {
                return@runStorage SaveCaseOutcome.AlreadyOpen
            }

            val id = TherapyCaseId(Uuid.random().toString())
            cases.upsertCase(
                TherapyCaseEntity(
                    id = id.value,
                    memberId = memberId.value,
                    condition = condition.trim(),
                    status = CaseStatus.ACTIVE.name,
                    openedOnEpochDay = openedOn.toEpochDays(),
                    closedOnEpochDay = null,
                    therapistStaffId = therapistStaffId,
                    referredBy = referredBy?.trim()?.ifBlank { null },
                    onset = onset.trim(),
                    mechanism = mechanism.trim(),
                    contraindications = contraindications?.trim()?.ifBlank { null },
                ),
            )
            SaveCaseOutcome.Saved(id)
        }
    }

    override suspend fun updateCase(
        caseId: TherapyCaseId,
        condition: String,
        therapistStaffId: String?,
        referredBy: String?,
        onset: String,
        mechanism: String,
        contraindications: String?,
    ): AppResult<SaveCaseOutcome> = withContext(dispatchers.io) {
        runStorage("Could not save the case") {
            val problems = validateCondition(condition)
            if (problems.isNotEmpty()) return@runStorage SaveCaseOutcome.Invalid(problems)

            val existing = cases.findById(caseId.value)
                ?: return@runStorage SaveCaseOutcome.Invalid(emptySet())
            cases.upsertCase(
                existing.copy(
                    condition = condition.trim(),
                    therapistStaffId = therapistStaffId,
                    referredBy = referredBy?.trim()?.ifBlank { null },
                    onset = onset.trim(),
                    mechanism = mechanism.trim(),
                    contraindications = contraindications?.trim()?.ifBlank { null },
                ),
            )
            SaveCaseOutcome.Saved(caseId)
        }
    }

    override suspend fun closeCase(caseId: TherapyCaseId, closedOn: LocalDate): AppResult<Unit> =
        withContext(dispatchers.io) {
            runStorage("Could not close the case") {
                val existing = cases.findById(caseId.value) ?: return@runStorage Unit
                cases.upsertCase(
                    existing.copy(
                        status = CaseStatus.CLOSED.name,
                        closedOnEpochDay = closedOn.toEpochDays(),
                    ),
                )
            }
        }

    override suspend fun logSession(
        caseId: TherapyCaseId,
        therapistStaffId: String?,
        at: Instant,
        durationMinutes: Int,
        treatmentTypes: Set<TreatmentType>,
        notes: String,
        painScore: Int?,
    ): AppResult<SaveSessionOutcome> = withContext(dispatchers.io) {
        runStorage("Could not log the session") {
            val problems = buildSet {
                if (durationMinutes !in
                    TherapySession.MIN_DURATION_MINUTES..TherapySession.MAX_DURATION_MINUTES
                ) {
                    add(SessionProblem.DURATION_OUT_OF_RANGE)
                }
                if (painScore != null && painScore !in TherapySession.PAIN_SCORE_RANGE) {
                    add(SessionProblem.PAIN_SCORE_OUT_OF_RANGE)
                }
            }
            if (problems.isNotEmpty()) return@runStorage SaveSessionOutcome.Invalid(problems)

            cases.upsertSession(
                TherapySessionEntity(
                    id = Uuid.random().toString(),
                    caseId = caseId.value,
                    therapistStaffId = therapistStaffId,
                    atEpochMs = at.toEpochMilliseconds(),
                    durationMinutes = durationMinutes,
                    treatmentTypes = treatmentTypes.joinToString(",") { it.name },
                    notes = notes.trim(),
                    painScore = painScore,
                ),
            )
            SaveSessionOutcome.Saved
        }
    }

    private fun validateCondition(condition: String): Set<CaseProblem> =
        if (condition.isBlank()) setOf(CaseProblem.CONDITION_BLANK) else emptySet()
}

private fun TherapyCaseEntity.toDomain() = TherapyCase(
    id = TherapyCaseId(id),
    memberId = MemberId(memberId),
    condition = condition,
    status = CaseStatus.entries.firstOrNull { it.name == status } ?: CaseStatus.ACTIVE,
    openedOn = LocalDate.fromEpochDays(openedOnEpochDay),
    closedOn = closedOnEpochDay?.let { LocalDate.fromEpochDays(it) },
    therapistStaffId = therapistStaffId,
    referredBy = referredBy,
    onset = onset,
    mechanism = mechanism,
    contraindications = contraindications,
)

private fun TherapySessionEntity.toDomain() = TherapySession(
    id = TherapySessionId(id),
    caseId = TherapyCaseId(caseId),
    therapistStaffId = therapistStaffId,
    at = Instant.fromEpochMilliseconds(atEpochMs),
    durationMinutes = durationMinutes,
    treatmentTypes = treatmentTypes.split(',')
        .mapNotNull { name -> TreatmentType.entries.firstOrNull { it.name == name } }
        .toSet(),
    notes = notes,
    painScore = painScore,
)
