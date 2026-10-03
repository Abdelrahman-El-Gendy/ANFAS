package com.anfas.feature.classes

import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.common.AppResult
import com.anfas.core.data.ClassProblem
import com.anfas.core.data.SaveClassOutcome
import com.anfas.core.data.Timetable
import com.anfas.core.model.ClassCategory
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ClassesComponentTest {

    private val owner = Session(userId = "s-owner", roles = setOf(Role.Owner))
    private val coach = Session(userId = "s-coach", roles = setOf(Role.Coach))

    // --- loading -------------------------------------------------------------------------

    @Test
    fun `a stored timetable loads with the whole week`() = runTest {
        val h = harness(classes = listOf(yoga(), boxing(day = DayOfWeek.FRIDAY)))

        val loaded = assertIs<ClassesContent.Loaded>(h.component.state.value.content)
        assertEquals(2, loaded.timetable.classes.size)
    }

    @Test
    fun `a repository failure is a Failed state carrying its message not an empty week`() =
        runTest {
            val h = harness(timetable = storageFailure("disk is full"))

            val failed = assertIs<ClassesContent.Failed>(h.component.state.value.content)
            assertEquals("disk is full", failed.message)
        }

    @Test
    fun `a timetable change underneath the screen is picked up`() = runTest {
        val h = harness(classes = listOf(yoga()))

        h.repository.timetable.value =
            AppResult.Success(Timetable(listOf(yoga(), boxing()), emptyMap()))

        assertEquals(2, h.component.state.value.timetable.classes.size)
    }

    // --- permission: viewing is free, changing is MANAGE_CLASSES --------------------------

    @Test
    fun `a coach can view the timetable but is not offered management`() = runTest {
        val h = harness(session = coach, classes = listOf(yoga()))

        assertIs<ClassesContent.Loaded>(h.component.state.value.content)
        assertFalse(h.component.state.value.mayManage)
    }

    @Test
    fun `a signed-out session may not manage either`() = runTest {
        val h = harness(session = null, classes = listOf(yoga()))

        assertFalse(h.component.state.value.mayManage)
    }

    @Test
    fun `without MANAGE_CLASSES the add and edit forms refuse to open`() = runTest {
        val h = harness(session = coach, classes = listOf(yoga()))

        h.component.onAddClass()
        assertNull(h.component.state.value.form)

        h.component.onEditClass(GymClassId("yoga"))
        assertNull(h.component.state.value.form)
    }

    @Test
    fun `without MANAGE_CLASSES a delete never reaches the repository`() = runTest {
        val h = harness(session = coach, classes = listOf(yoga()))

        h.component.onDeleteClass(GymClassId("yoga"))

        assertTrue(h.repository.deleted.isEmpty())
        assertNull(h.component.state.value.notice)
    }

    /**
     * Hiding the button is UX; the component is the enforcement. The form is open here because the
     * session was an owner's a moment ago, which is exactly the demotion-under-a-live-screen case.
     */
    @Test
    fun `a session demoted while the form is open cannot submit it`() = runTest {
        val h = harness(session = owner, classes = emptyList())
        h.component.onAddClass()
        h.component.onFormChanged { it.copy(name = "Spin", room = "Studio A", capacity = "12") }

        h.auth.session.value = coach
        h.component.onSubmitForm()

        assertTrue(h.repository.saved.isEmpty(), "a demoted session must not write")
    }

    // --- saving -------------------------------------------------------------------------

    @Test
    fun `a saved class closes the form names itself in the notice and carries the typed fields`() =
        runTest {
            val h = harness(session = owner, classes = emptyList(), ids = listOf("new-1"))
            h.component.onAddClass()
            h.component.onFormChanged {
                it.copy(
                    name = "  Spin  ",
                    category = ClassCategory.RECOVERY,
                    room = "Studio A",
                    capacity = " 12 ",
                    dayOfWeek = DayOfWeek.TUESDAY,
                    startHour = 7,
                    startMinute = 30,
                    durationMinutes = 45,
                    instructorStaffId = "s-9",
                )
            }

            h.component.onSubmitForm()

            val saved = h.repository.saved.single()
            assertEquals(GymClassId("new-1"), saved.id)
            assertEquals(12, saved.capacity)
            assertEquals(DayOfWeek.TUESDAY, saved.dayOfWeek)
            assertEquals(LocalTime(7, 30), saved.startsAt)
            assertEquals(45, saved.durationMinutes)
            assertEquals("s-9", saved.instructorStaffId)
            assertEquals(ClassCategory.RECOVERY, saved.category)
            assertNull(h.component.state.value.form)
            assertEquals(ClassesNotice.Saved("Spin"), h.component.state.value.notice)
        }

    /** A room clash is a warning. The row is written either way and the form closes. */
    @Test
    fun `a room clash is saved anyway and names the other class`() = runTest {
        val h = harness(session = owner, classes = listOf(yoga()))
        h.repository.saveResult =
            AppResult.Success(SaveClassOutcome.SavedWithRoomClash(listOf(yoga())))
        h.component.onAddClass()
        h.component.onFormChanged { it.copy(name = "Pilates", room = "Studio A", capacity = "8") }

        h.component.onSubmitForm()

        assertEquals(1, h.repository.saved.size, "the clash must not stop the write")
        assertNull(h.component.state.value.form, "a saved row closes the form")
        assertEquals(
            ClassesNotice.RoomClash(room = "Studio A", otherClassName = "Yoga"),
            h.component.state.value.notice,
        )
    }

    @Test
    fun `an invalid save keeps the form open with the fields marked and what was typed`() =
        runTest {
            val h = harness(session = owner, classes = emptyList())
            h.repository.saveResult = AppResult.Success(
                SaveClassOutcome.Invalid(setOf(ClassProblem.ROOM_BLANK)),
            )
            h.component.onAddClass()
            h.component.onFormChanged { it.copy(name = "Spin", capacity = "12") }

            h.component.onSubmitForm()

            val form = assertNotNull(h.component.state.value.form)
            assertEquals(setOf(ClassProblem.ROOM_BLANK), form.problems)
            assertEquals("Spin", form.name)
            assertFalse(form.isSubmitting, "a rejected form must be submittable again")
        }

    @Test
    fun `touching a field clears the marked problems`() = runTest {
        val h = harness(session = owner, classes = emptyList())
        h.repository.saveResult = AppResult.Success(
            SaveClassOutcome.Invalid(setOf(ClassProblem.ROOM_BLANK)),
        )
        h.component.onAddClass()
        h.component.onFormChanged { it.copy(name = "Spin") }
        h.component.onSubmitForm()

        h.component.onFormChanged { it.copy(room = "Studio B") }

        assertTrue(assertNotNull(h.component.state.value.form).problems.isEmpty())
    }

    /** A silent default would be a number nobody chose. */
    @Test
    fun `an unparseable capacity is sent as zero for the repository to reject`() = runTest {
        val h = harness(session = owner, classes = emptyList())
        h.component.onAddClass()
        h.component.onFormChanged { it.copy(name = "Spin", room = "A", capacity = "lots") }

        h.component.onSubmitForm()

        assertEquals(0, h.repository.saved.single().capacity)
    }

    @Test
    fun `a blank name cannot be submitted at all`() = runTest {
        val h = harness(session = owner, classes = emptyList())
        h.component.onAddClass()
        h.component.onFormChanged { it.copy(name = "   ", room = "A", capacity = "10") }

        assertFalse(assertNotNull(h.component.state.value.form).canSubmit)
        h.component.onSubmitForm()

        assertTrue(h.repository.saved.isEmpty())
    }

    @Test
    fun `a repository failure on save keeps the form and reports it`() = runTest {
        val h = harness(session = owner, classes = emptyList())
        h.repository.saveResult = storageFailure("locked")
        h.component.onAddClass()
        h.component.onFormChanged { it.copy(name = "Spin", room = "A", capacity = "10") }

        h.component.onSubmitForm()

        val form = assertNotNull(h.component.state.value.form)
        assertFalse(form.isSubmitting)
        assertEquals(ClassesNotice.Failed("locked"), h.component.state.value.notice)
    }

    @Test
    fun `editing prefills from the row and saves under the same id`() = runTest {
        val h = harness(session = owner, classes = listOf(yoga()), ids = listOf("unused"))

        h.component.onEditClass(GymClassId("yoga"))
        val form = assertNotNull(h.component.state.value.form)
        assertTrue(form.isEditing)
        assertEquals("Yoga", form.name)
        assertEquals("20", form.capacity)

        h.component.onFormChanged { it.copy(name = "Hot Yoga") }
        h.component.onSubmitForm()

        val saved = h.repository.saved.single()
        assertEquals(GymClassId("yoga"), saved.id, "an edit is an update, never a new row")
        assertEquals("Hot Yoga", saved.name)
    }

    @Test
    fun `editing an id that is not on the timetable opens nothing`() = runTest {
        val h = harness(session = owner, classes = listOf(yoga()))

        h.component.onEditClass(GymClassId("ghost"))

        assertNull(h.component.state.value.form)
    }

    @Test
    fun `the add form opens on the day being looked at`() = runTest {
        val h = harness(session = owner, classes = emptyList())
        h.component.onDaySelected(DayOfWeek.THURSDAY)

        h.component.onAddClass()

        assertEquals(DayOfWeek.THURSDAY, assertNotNull(h.component.state.value.form).dayOfWeek)
    }

    // --- deleting -----------------------------------------------------------------------

    @Test
    fun `deleting a class reaches the repository and names it in the notice`() = runTest {
        val h = harness(session = owner, classes = listOf(yoga()))

        h.component.onDeleteClass(GymClassId("yoga"))

        assertEquals(listOf(GymClassId("yoga")), h.repository.deleted)
        assertEquals(ClassesNotice.Deleted("Yoga"), h.component.state.value.notice)
    }

    @Test
    fun `a failed delete is reported rather than swallowed`() = runTest {
        val h = harness(session = owner, classes = listOf(yoga()))
        h.repository.deleteResult = storageFailure("locked")

        h.component.onDeleteClass(GymClassId("yoga"))

        assertEquals(ClassesNotice.Failed("locked"), h.component.state.value.notice)
    }

    @Test
    fun `deleting an unknown id does nothing`() = runTest {
        val h = harness(session = owner, classes = listOf(yoga()))

        h.component.onDeleteClass(GymClassId("ghost"))

        assertTrue(h.repository.deleted.isEmpty())
    }

    // --- day selection and filters ------------------------------------------------------

    /** 2026-08-26 is a Wednesday. */
    @Test
    fun `the selected day follows the clock until someone picks one`() = runTest {
        val h = harness(classes = emptyList())
        assertEquals(DayOfWeek.WEDNESDAY, h.component.state.value.selectedDay)
        assertEquals(DayOfWeek.WEDNESDAY, h.component.state.value.today)

        h.component.onDaySelected(DayOfWeek.SATURDAY)
        assertEquals(DayOfWeek.SATURDAY, h.component.state.value.selectedDay)
        assertEquals(DayOfWeek.WEDNESDAY, h.component.state.value.today, "today does not move")

        h.component.onTodaySelected()
        assertEquals(DayOfWeek.WEDNESDAY, h.component.state.value.selectedDay)
    }

    @Test
    fun `filters narrow the visible classes and clearing them restores the week`() = runTest {
        val h = harness(
            classes = listOf(yoga(), boxing()),
            names = mapOf("s-1" to "Sara", "s-2" to "Omar"),
        )

        h.component.onInstructorFilterChanged("s-2")
        assertEquals(listOf("Boxing"), h.component.state.value.visibleClasses.map { it.name })
        assertTrue(h.component.state.value.isFiltered)

        h.component.onInstructorFilterChanged(null)
        h.component.onRoomFilterChanged("Studio A")
        assertEquals(listOf("Yoga"), h.component.state.value.visibleClasses.map { it.name })

        h.component.onRoomFilterChanged(null)
        assertEquals(2, h.component.state.value.visibleClasses.size)
        assertFalse(h.component.state.value.isFiltered)
    }

    @Test
    fun `the coach filter offers only coaches who teach something and still resolve`() = runTest {
        // s-3 teaches nothing; s-2 teaches but no longer resolves to a name.
        val h = harness(
            classes = listOf(yoga(), boxing()),
            names = mapOf("s-1" to "Sara", "s-3" to "Idle"),
        )

        assertEquals(listOf("s-1" to "Sara"), h.component.state.value.instructors)
    }

    // --- harness ------------------------------------------------------------------------

    private class Harness(
        val component: ClassesComponent,
        val repository: FakeClassRepository,
        val auth: FakeAuth,
    )

    private fun TestScope.harness(
        session: Session? = owner,
        classes: List<GymClass> = emptyList(),
        names: Map<String, String> = emptyMap(),
        timetable: AppResult<Timetable> = AppResult.Success(Timetable(classes, names)),
        ids: List<String> = listOf("generated"),
    ): Harness {
        val repository = FakeClassRepository(timetable)
        val auth = FakeAuth(session)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val lifecycle = LifecycleRegistry()
        // An iterator, not `removeFirst()`: on the Android host target that call binds to a JDK 21
        // method the Android SDK lacks, and the resulting error was swallowed by the component's
        // exception handler, so the save silently never happened.
        val remaining = ids.iterator()
        val component = ClassesComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = repository,
            auth = auth,
            dispatchers = TestDispatchers(dispatcher),
            clock = FixedClock(Instant.parse("2026-08-26T10:00:00Z")),
            zone = TimeZone.UTC,
            newId = { remaining.next() },
        )
        lifecycle.resume()
        // `state` is stateIn(WhileSubscribed): keep a subscriber so `.value` is live.
        backgroundScope.launch(dispatcher) { component.state.collect { } }
        return Harness(component, repository, auth)
    }

    private fun yoga() = GymClass(
        id = GymClassId("yoga"),
        name = "Yoga",
        category = ClassCategory.RECOVERY,
        room = "Studio A",
        capacity = 20,
        instructorStaffId = "s-1",
        dayOfWeek = DayOfWeek.MONDAY,
        startsAt = LocalTime(9, 0),
        durationMinutes = 60,
    )

    private fun boxing(day: DayOfWeek = DayOfWeek.MONDAY) = GymClass(
        id = GymClassId("boxing"),
        name = "Boxing",
        category = ClassCategory.GENERAL,
        room = "Ring",
        capacity = 12,
        instructorStaffId = "s-2",
        dayOfWeek = day,
        startsAt = LocalTime(18, 0),
        durationMinutes = 45,
    )
}
