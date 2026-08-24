package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.database.StaffDao
import com.anfas.core.database.StaffEntity
import com.anfas.core.database.TherapyCaseDao
import com.anfas.core.database.TherapyCaseEntity
import com.anfas.core.database.TherapySessionEntity
import com.anfas.core.model.CaseStatus
import com.anfas.core.model.MemberId
import com.anfas.core.model.TherapyCaseId
import com.anfas.core.model.TherapySession
import com.anfas.core.model.TreatmentType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TherapyRepositoryTest {

    private val memberId = MemberId("m-1")
    private val openedOn = LocalDate.parse("2026-06-03")

    @Test
    fun `a member with no case reads as null not a failure`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        repository.observeLatestCase(memberId).test {
            assertNull(awaitItem().valueOrFail())
        }
    }

    @Test
    fun `an opened case is readable back with the given fields`() = runTest {
        val repository = repository(FakeTherapyCaseDao())

        val outcome = repository.openCase(
            memberId = memberId,
            condition = "  Right shoulder impingement  ",
            therapistStaffId = "s-1",
            referredBy = "Dr. Amira Saleh",
            onset = "Late May 2026",
            mechanism = "Gradual onset during overhead serves.",
            contraindications = "No overhead pressing.",
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId

        repository.observeLatestCase(memberId).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals(caseId, detail.case.id)
            assertEquals("Right shoulder impingement", detail.case.condition)
            assertEquals(CaseStatus.ACTIVE, detail.case.status)
            assertEquals("Dr. Amira Saleh", detail.case.referredBy)
            assertEquals("No overhead pressing.", detail.case.contraindications)
            assertEquals(openedOn, detail.case.openedOn)
            assertNull(detail.case.closedOn)
        }
    }

    @Test
    fun `a blank condition is refused and nothing is written`() = runTest {
        val dao = FakeTherapyCaseDao()
        val repository = repository(dao)

        val outcome = repository.openCase(
            memberId = memberId,
            condition = "   ",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()

        assertEquals(
            setOf(CaseProblem.CONDITION_BLANK),
            assertIs<SaveCaseOutcome.Invalid>(outcome).problems,
        )
        repository.observeLatestCase(memberId).test {
            assertNull(awaitItem().valueOrFail())
        }
    }

    /** The export's record is singular per patient — a second simultaneous case is refused. */
    @Test
    fun `opening a case while one is already active is refused`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()

        val second = repository.openCase(
            memberId = memberId,
            condition = "Lower back pain",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()

        assertEquals(SaveCaseOutcome.AlreadyOpen, second)
    }

    /** A closed case is history, not a lock — a new one may be opened for a new injury. */
    @Test
    fun `a new case may be opened once the previous one is closed`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val first = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val firstId = (assertIs<SaveCaseOutcome.Saved>(first)).caseId
        repository.closeCase(firstId, closedOn = LocalDate.parse("2026-07-01")).valueOrFail()

        val second = repository.openCase(
            memberId = memberId,
            condition = "Lower back pain",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = LocalDate.parse("2026-07-02"),
        ).valueOrFail()

        assertIs<SaveCaseOutcome.Saved>(second)
        repository.observeLatestCase(memberId).test {
            assertEquals("Lower back pain", awaitItem().valueOrFail()!!.case.condition)
        }
    }

    @Test
    fun `closing a case stamps closedOn and flips status without deleting it`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val outcome = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId
        val closedOn = LocalDate.parse("2026-08-01")

        repository.closeCase(caseId, closedOn).valueOrFail()

        repository.observeLatestCase(memberId).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals(CaseStatus.CLOSED, detail.case.status)
            assertEquals(closedOn, detail.case.closedOn)
        }
    }

    @Test
    fun `updating a case changes its fields without touching status`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val outcome = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId

        repository.updateCase(
            caseId = caseId,
            condition = "Right shoulder impingement, revised",
            therapistStaffId = "s-2",
            referredBy = "Dr. Amira Saleh",
            onset = "Late May 2026",
            mechanism = "Overhead serves.",
            contraindications = "No barbell bench.",
        ).valueOrFail()

        repository.observeLatestCase(memberId).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals("Right shoulder impingement, revised", detail.case.condition)
            assertEquals("s-2", detail.case.therapistStaffId)
            assertEquals(CaseStatus.ACTIVE, detail.case.status)
        }
    }

    @Test
    fun `a therapist is named from the staff table and a rename is reflected`() = runTest {
        val staff = FakeTherapyStaff(mapOf("s-1" to "Dr. Youssef"))
        val repository = repository(FakeTherapyCaseDao(), staff)
        repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = "s-1",
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()

        repository.observeLatestCase(memberId).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals("Dr. Youssef", detail.therapistName(detail.case.therapistStaffId))
        }

        staff.rename("s-1", "Dr. Youssef Hassan")
        repository.observeLatestCase(memberId).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals("Dr. Youssef Hassan", detail.therapistName(detail.case.therapistStaffId))
        }
    }

    @Test
    fun `an unresolvable therapist reads as unassigned`() = runTest {
        val repository = repository(FakeTherapyCaseDao(), FakeTherapyStaff(emptyMap()))
        repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = "s-gone",
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()

        repository.observeLatestCase(memberId).test {
            val detail = awaitItem().valueOrFail()!!
            assertNull(detail.therapistName(detail.case.therapistStaffId))
        }
    }

    @Test
    fun `a logged session appears newest first`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val outcome = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId

        repository.logSession(
            caseId = caseId,
            therapistStaffId = "s-1",
            at = Instant.fromEpochSeconds(100),
            durationMinutes = 60,
            treatmentTypes = setOf(TreatmentType.ULTRASOUND, TreatmentType.MANUAL_THERAPY),
            notes = "Deep tissue work.",
            painScore = 5,
        ).valueOrFail()
        repository.logSession(
            caseId = caseId,
            therapistStaffId = "s-1",
            at = Instant.fromEpochSeconds(200),
            durationMinutes = 45,
            treatmentTypes = setOf(TreatmentType.DRY_NEEDLING),
            notes = "Immediate relief.",
            painScore = 3,
        ).valueOrFail()

        repository.observeLatestCase(memberId).test {
            val sessions = awaitItem().valueOrFail()!!.sessions
            assertEquals(
                listOf("Immediate relief.", "Deep tissue work."),
                sessions.map {
                    it.notes
                },
            )
            assertEquals(setOf(TreatmentType.DRY_NEEDLING), sessions.first().treatmentTypes)
        }
    }

    @Test
    fun `a session duration out of range is refused`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val outcome = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId

        val result = repository.logSession(
            caseId = caseId,
            therapistStaffId = null,
            at = Instant.fromEpochSeconds(100),
            durationMinutes = 0,
            treatmentTypes = emptySet(),
            notes = "",
            painScore = null,
        ).valueOrFail()

        assertEquals(
            setOf(SessionProblem.DURATION_OUT_OF_RANGE),
            assertIs<SaveSessionOutcome.Invalid>(result).problems,
        )
    }

    @Test
    fun `a pain score outside 0 to 10 is refused`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val outcome = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId

        val result = repository.logSession(
            caseId = caseId,
            therapistStaffId = null,
            at = Instant.fromEpochSeconds(100),
            durationMinutes = 30,
            treatmentTypes = emptySet(),
            notes = "",
            painScore = 11,
        ).valueOrFail()

        assertEquals(
            setOf(SessionProblem.PAIN_SCORE_OUT_OF_RANGE),
            assertIs<SaveSessionOutcome.Invalid>(result).problems,
        )
    }

    /** A session with no treatments and no notes is a valid consultation-only visit. */
    @Test
    fun `a session with no treatment types is accepted`() = runTest {
        val repository = repository(FakeTherapyCaseDao())
        val outcome = repository.openCase(
            memberId = memberId,
            condition = "Right shoulder impingement",
            therapistStaffId = null,
            referredBy = null,
            onset = "",
            mechanism = "",
            contraindications = null,
            openedOn = openedOn,
        ).valueOrFail()
        val caseId = (assertIs<SaveCaseOutcome.Saved>(outcome)).caseId

        val result = repository.logSession(
            caseId = caseId,
            therapistStaffId = null,
            at = Instant.fromEpochSeconds(100),
            durationMinutes = 30,
            treatmentTypes = emptySet(),
            notes = "",
            painScore = null,
        ).valueOrFail()

        assertEquals(SaveSessionOutcome.Saved, result)
    }

    private fun repository(
        dao: FakeTherapyCaseDao,
        staff: FakeTherapyStaff = FakeTherapyStaff(emptyMap()),
    ): TherapyRepository = OfflineFirstTherapyRepository(
        cases = dao,
        staff = staff,
        dispatchers = UnconfinedDispatchers,
    )
}

private class FakeTherapyCaseDao : TherapyCaseDao {
    private val caseRows = MutableStateFlow<List<TherapyCaseEntity>>(emptyList())
    private val sessionRows = MutableStateFlow<List<TherapySessionEntity>>(emptyList())

    override fun observeLatestForMember(memberId: String): Flow<TherapyCaseEntity?> =
        caseRows.map { list ->
            list.filter { it.memberId == memberId }.maxByOrNull { it.openedOnEpochDay }
        }

    override suspend fun findLatestForMember(memberId: String): TherapyCaseEntity? =
        caseRows.value.filter { it.memberId == memberId }.maxByOrNull { it.openedOnEpochDay }

    override suspend fun findById(id: String): TherapyCaseEntity? =
        caseRows.value.firstOrNull { it.id == id }

    override suspend fun upsertCase(case: TherapyCaseEntity) {
        caseRows.value = caseRows.value.filterNot { it.id == case.id } + case
    }

    override fun observeSessions(caseId: String): Flow<List<TherapySessionEntity>> =
        sessionRows.map { list ->
            list.filter { it.caseId == caseId }.sortedByDescending { it.atEpochMs }
        }

    override suspend fun upsertSession(session: TherapySessionEntity) {
        sessionRows.value = sessionRows.value.filterNot { it.id == session.id } + session
    }
}

private class FakeTherapyStaff(initial: Map<String, String>) : StaffDao {
    private val rows = MutableStateFlow(initial)

    fun rename(id: String, name: String) {
        rows.value = rows.value + (id to name)
    }

    override fun observeAll(): Flow<List<StaffEntity>> = rows.map { map ->
        map.map { (id, name) -> staffEntity(id, name) }
    }

    override suspend fun findByUsername(username: String): StaffEntity? = null
    override suspend fun findById(id: String): StaffEntity? =
        rows.value[id]?.let { staffEntity(id, it) }

    override fun observeById(id: String): Flow<StaffEntity?> =
        rows.map { map -> map[id]?.let { staffEntity(id, it) } }

    override suspend fun count(): Int = rows.value.size
    override suspend fun allUsernames(): List<String> = rows.value.keys.toList()
    override suspend fun upsert(staff: StaffEntity) = Unit
    override suspend fun delete(id: String) = Unit

    private fun staffEntity(id: String, name: String) = StaffEntity(
        id = id,
        username = name.lowercase().replace(' ', '.'),
        displayName = name,
        roles = "Therapist",
        passwordAlgorithm = "PBKDF2WithHmacSHA256",
        passwordIterations = 1,
        passwordSalt = ByteArray(1),
        passwordHash = ByteArray(1),
        createdAtEpochMs = 0L,
        isEnabled = true,
    )
}
