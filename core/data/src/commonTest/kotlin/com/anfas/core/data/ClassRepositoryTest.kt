package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.database.GymClassDao
import com.anfas.core.database.GymClassEntity
import com.anfas.core.database.StaffDao
import com.anfas.core.database.StaffEntity
import com.anfas.core.database.SyncOp
import com.anfas.core.database.SyncOutboxEntity
import com.anfas.core.database.SyncTables
import com.anfas.core.database.SyncTombstoneEntity
import com.anfas.core.model.ClassCategory
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import com.anfas.core.model.toMinuteOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClassRepositoryTest {

    private fun gymClass(
        id: String = "c-1",
        name: String = "HIIT",
        room: String = "Studio A",
        capacity: Int = 20,
        day: DayOfWeek = DayOfWeek.MONDAY,
        start: String = "08:00",
        minutes: Int = 60,
        instructor: String? = null,
    ) = GymClass(
        id = GymClassId(id),
        name = name,
        category = ClassCategory.GENERAL,
        room = room,
        capacity = capacity,
        instructorStaffId = instructor,
        dayOfWeek = day,
        startsAt = LocalTime.parse(start),
        durationMinutes = minutes,
    )

    @Test
    fun `a saved class comes back on the timetable`() = runTest {
        val dao = FakeGymClassDao()
        val repository = repository(dao)

        assertEquals(SaveClassOutcome.Saved, repository.save(gymClass()).valueOrFail())

        repository.observeTimetable().test {
            val timetable = awaitItem().valueOrFail()
            assertEquals(listOf("HIIT"), timetable.classes.map { it.name })
            assertEquals(DayOfWeek.MONDAY, timetable.classes.single().dayOfWeek)
            assertEquals(LocalTime.parse("08:00"), timetable.classes.single().startsAt)
        }
    }

    /**
     * The name is resolved from `staff`, not stored on the row, so this is what proves a rename
     * reaches the timetable.
     */
    @Test
    fun `an instructor is named from the staff table`() = runTest {
        val dao = FakeGymClassDao()
        val staff = FakeStaff(mapOf("s-1" to "Coach Tarek"))
        val repository = repository(dao, staff)
        repository.save(gymClass(instructor = "s-1")).valueOrFail()

        repository.observeTimetable().test {
            val timetable = awaitItem().valueOrFail()
            assertEquals("Coach Tarek", timetable.instructorFor(timetable.classes.single()))
        }

        staff.rename("s-1", "Coach T. Hassan")
        repository.observeTimetable().test {
            val timetable = awaitItem().valueOrFail()
            assertEquals("Coach T. Hassan", timetable.instructorFor(timetable.classes.single()))
        }
    }

    /** An id that no longer resolves reads as unassigned, not as a crash and not as a blank name. */
    @Test
    fun `an unresolvable instructor reads as unassigned`() = runTest {
        val repository = repository(FakeGymClassDao(), FakeStaff(emptyMap()))
        repository.save(gymClass(instructor = "s-gone")).valueOrFail()

        repository.observeTimetable().test {
            val timetable = awaitItem().valueOrFail()
            assertNull(timetable.instructorFor(timetable.classes.single()))
        }
    }

    @Test
    fun `a class with no instructor is unassigned`() = runTest {
        val repository = repository(FakeGymClassDao())
        repository.save(gymClass(instructor = null)).valueOrFail()

        repository.observeTimetable().test {
            val timetable = awaitItem().valueOrFail()
            assertNull(timetable.instructorFor(timetable.classes.single()))
        }
    }

    /** Saved, and reported — a room double-booking is a question for staff, not a rejection. */
    @Test
    fun `an overlapping class in the same room is saved with a clash warning`() = runTest {
        val dao = FakeGymClassDao()
        val repository = repository(dao)
        repository.save(gymClass(id = "c-1", start = "08:00", minutes = 60)).valueOrFail()

        val outcome = repository
            .save(gymClass(id = "c-2", name = "Yoga", start = "08:30", minutes = 60))
            .valueOrFail()

        val clash = assertIs<SaveClassOutcome.SavedWithRoomClash>(outcome)
        assertEquals(listOf("HIIT"), clash.clashing.map { it.name })
        // Saved despite the warning.
        repository.observeTimetable().test {
            assertEquals(2, awaitItem().valueOrFail().classes.size)
        }
    }

    @Test
    fun `a different room at the same time is not a clash`() = runTest {
        val repository = repository(FakeGymClassDao())
        repository.save(gymClass(id = "c-1", room = "Studio A")).valueOrFail()

        assertEquals(
            SaveClassOutcome.Saved,
            repository.save(gymClass(id = "c-2", room = "Zen Studio")).valueOrFail(),
        )
    }

    @Test
    fun `a different day in the same room is not a clash`() = runTest {
        val repository = repository(FakeGymClassDao())
        repository.save(gymClass(id = "c-1", day = DayOfWeek.MONDAY)).valueOrFail()

        assertEquals(
            SaveClassOutcome.Saved,
            repository.save(gymClass(id = "c-2", day = DayOfWeek.TUESDAY)).valueOrFail(),
        )
    }

    /**
     * Editing a class must not report it clashing with its own previous version. The check reads
     * back after the write and excludes the row's own id, which is what makes this pass.
     */
    @Test
    fun `editing a class does not clash with itself`() = runTest {
        val repository = repository(FakeGymClassDao())
        repository.save(gymClass(id = "c-1", start = "08:00")).valueOrFail()

        val outcome = repository
            .save(gymClass(id = "c-1", name = "HIIT Foundation", start = "08:15"))
            .valueOrFail()

        assertEquals(SaveClassOutcome.Saved, outcome)
        repository.observeTimetable().test {
            assertEquals(
                listOf("HIIT Foundation"),
                awaitItem().valueOrFail().classes.map {
                    it.name
                },
            )
        }
    }

    @Test
    fun `a blank name is refused and nothing is written`() = runTest {
        val dao = FakeGymClassDao()
        val repository = repository(dao)

        val outcome = repository.save(gymClass(name = "   ")).valueOrFail()

        assertEquals(
            setOf(ClassProblem.NAME_BLANK),
            assertIs<SaveClassOutcome.Invalid>(outcome).problems,
        )
        repository.observeTimetable().test {
            assertTrue(awaitItem().valueOrFail().isEmpty)
        }
    }

    @Test
    fun `every field problem is reported at once so the form can mark them all`() = runTest {
        val repository = repository(FakeGymClassDao())

        val outcome = repository
            .save(gymClass(name = "", room = "", capacity = 0, minutes = 0))
            .valueOrFail()

        assertEquals(
            setOf(
                ClassProblem.NAME_BLANK,
                ClassProblem.ROOM_BLANK,
                ClassProblem.CAPACITY_OUT_OF_RANGE,
                ClassProblem.DURATION_OUT_OF_RANGE,
            ),
            assertIs<SaveClassOutcome.Invalid>(outcome).problems,
        )
    }

    @Test
    fun `a deleted class leaves the timetable`() = runTest {
        val repository = repository(FakeGymClassDao())
        repository.save(gymClass(id = "c-1")).valueOrFail()

        repository.delete(GymClassId("c-1")).valueOrFail()

        repository.observeTimetable().test {
            assertTrue(awaitItem().valueOrFail().isEmpty)
        }
    }

    /** Whitespace is trimmed on the way in, or "Studio A " and "Studio A" become two rooms. */
    @Test
    fun `name and room are trimmed`() = runTest {
        val repository = repository(FakeGymClassDao())
        repository.save(gymClass(name = "  HIIT  ", room = " Studio A ")).valueOrFail()

        repository.observeTimetable().test {
            val saved = awaitItem().valueOrFail().classes.single()
            assertEquals("HIIT", saved.name)
            assertEquals("Studio A", saved.room)
        }
    }

    /**
     * The two units the table stores, pinned. `day_of_week` is an ISO number so the week sorts,
     * and `start_minute_of_day` is minutes since midnight so times sort as integers. If either
     * assumption changed, every stored row would be misread rather than fail to parse.
     */
    @Test
    fun `the stored units are minutes since midnight and ISO day numbers`() {
        assertEquals(510, LocalTime.parse("08:30").toMinuteOfDay())
        assertEquals(1, DayOfWeek.MONDAY.isoDayNumber)
        assertEquals(7, DayOfWeek.SUNDAY.isoDayNumber)
    }

    /**
     * Every write path in this repository files an outbox entry — the coverage `design/sync-layer.md`
     * calls the point of this stage. A write that commits without one is a change that never
     * syncs, and nothing afterwards can detect that it happened.
     */
    @Test
    fun `saving a class files an upsert and deleting one files a delete and a tombstone`() =
        runTest {
            val dao = FakeGymClassDao()
            val repository = repository(dao)

            repository.save(gymClass()).valueOrFail()
            assertEquals(
                listOf(SyncTables.SCHEDULED_CLASSES to SyncOp.UPSERT.name),
                dao.sync.recorded,
            )

            repository.delete(GymClassId("c-1")).valueOrFail()
            assertEquals(listOf("c-1"), dao.sync.deletes(SyncTables.SCHEDULED_CLASSES))
            assertEquals(listOf("c-1"), dao.sync.tombstoned(SyncTables.SCHEDULED_CLASSES))
        }

    private fun repository(
        dao: FakeGymClassDao,
        staff: FakeStaff = FakeStaff(emptyMap()),
    ): ClassRepository = OfflineFirstClassRepository(
        classes = dao,
        staff = staff,
        dispatchers = UnconfinedDispatchers,
    )
}

internal class FakeGymClassDao : GymClassDao {
    private val rows = MutableStateFlow<List<GymClassEntity>>(emptyList())

    override fun observeAll(): Flow<List<GymClassEntity>> = rows.map { list ->
        list.sortedWith(
            compareBy({
                it.dayOfWeek
            }, { it.startMinuteOfDay }, { it.name.lowercase() }, { it.id }),
        )
    }

    override suspend fun findById(id: String): GymClassEntity? = rows.value.firstOrNull {
        it.id ==
            id
    }

    override suspend fun findClashesInRoom(
        room: String,
        dayOfWeek: Int,
        excludeId: String,
    ): List<GymClassEntity> = rows.value.filter {
        it.room == room && it.dayOfWeek == dayOfWeek && it.id != excludeId
    }

    override suspend fun upsert(gymClass: GymClassEntity) {
        rows.value = rows.value.filterNot { it.id == gymClass.id } + gymClass
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun unassignInstructor(staffId: String) {
        rows.value = rows.value.map {
            if (it.instructorStaffId == staffId) it.copy(instructorStaffId = null) else it
        }
    }

    // --- sync bookkeeping. The tracked writes are default methods on the DAO, so implementing
    // these two gives this fake the production sequencing rather than a re-implementation of it.
    val sync = OutboxRecorder()

    override suspend fun recordChange(entry: SyncOutboxEntity) = sync.record(entry)

    override suspend fun recordTombstones(entries: List<SyncTombstoneEntity>) = sync.record(entries)

    override suspend fun idsForInstructor(staffId: String): List<String> =
        rows.value.filter { it.instructorStaffId == staffId }.map { it.id }
}

internal class FakeStaff(initial: Map<String, String>) : StaffDao {
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
        roles = "Coach",
        passwordAlgorithm = "PBKDF2WithHmacSHA256",
        passwordIterations = 1,
        passwordSalt = ByteArray(1),
        passwordHash = ByteArray(1),
        createdAtEpochMs = 0L,
        isEnabled = true,
    )
}
