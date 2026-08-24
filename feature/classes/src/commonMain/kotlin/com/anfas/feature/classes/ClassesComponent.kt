package com.anfas.feature.classes

import com.anfas.core.auth.Permission
import com.anfas.core.auth.can
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.ClassRepository
import com.anfas.core.data.SaveClassOutcome
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import com.anfas.core.model.toMinuteOfDay
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The weekly timetable, for both designed screens — the mobile day view and the desktop grid.
 *
 * One component for both because they show the same data with the same actions; the layout choice
 * belongs to the screen, which already picks by width everywhere else in this app. Splitting them
 * would mean two copies of the filter and form logic that must agree.
 *
 * The clock is sampled **once per state build**, not polled. The only thing it feeds is the
 * current-time line and the finished/upcoming split, neither of which needs to be live to the
 * minute; a ticking flow would recompose the whole grid every second for a line that moves one
 * pixel a minute.
 */
class ClassesComponent(
    componentContext: ComponentContext,
    private val repository: ClassRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val newId: () -> String,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("Classes"))

    private val ui = MutableStateFlow(UiState())

    val state: StateFlow<ClassesState> = combine(
        ui,
        auth.observeSession(),
        repository.observeTimetable(),
    ) { local, session, timetableResult ->
        val now = clock.now().toLocalDateTime(zone)
        ClassesState(
            content = when (timetableResult) {
                is AppResult.Failure -> ClassesContent.Failed(timetableResult.error.message)
                is AppResult.Success -> ClassesContent.Loaded(timetableResult.value)
            },
            // Defaults to today until someone picks a day, which is what "Today's schedule"
            // means — but a chosen day survives the timetable changing underneath it.
            selectedDay = local.selectedDay ?: now.date.dayOfWeek,
            today = now.date.dayOfWeek,
            nowMinuteOfDay = now.time.toMinuteOfDay(),
            form = local.form,
            notice = local.notice,
            mayManage = session?.can(Permission.MANAGE_CLASSES) == true,
            instructorFilter = local.instructorFilter,
            roomFilter = local.roomFilter,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ClassesState(),
    )

    fun onDaySelected(day: DayOfWeek) = ui.update { it.copy(selectedDay = day) }

    /** Back to following the clock, rather than to a hardcoded day. */
    fun onTodaySelected() = ui.update { it.copy(selectedDay = null) }

    fun onInstructorFilterChanged(staffId: String?) =
        ui.update { it.copy(instructorFilter = staffId) }

    fun onRoomFilterChanged(room: String?) = ui.update { it.copy(roomFilter = room) }

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    fun onAddClass() {
        val current = state.value
        if (!current.mayManage) return
        ui.update {
            it.copy(
                form = ClassForm(
                    // Opens on the day being looked at, which is nearly always the one being
                    // filled in.
                    dayOfWeek = current.selectedDay,
                    instructorOptions = current.instructors,
                ),
                notice = null,
            )
        }
    }

    fun onEditClass(id: GymClassId) {
        val current = state.value
        if (!current.mayManage) return
        val target = current.timetable.classes.firstOrNull { it.id == id } ?: return
        ui.update {
            it.copy(
                form = ClassForm(
                    editing = target.id,
                    name = target.name,
                    category = target.category,
                    room = target.room,
                    capacity = target.capacity.toString(),
                    instructorStaffId = target.instructorStaffId,
                    dayOfWeek = target.dayOfWeek,
                    startHour = target.startsAt.hour,
                    startMinute = target.startsAt.minute,
                    durationMinutes = target.durationMinutes,
                    instructorOptions = current.instructors,
                ),
                notice = null,
            )
        }
    }

    fun onFormDismissed() = ui.update { it.copy(form = null) }

    fun onFormChanged(transform: (ClassForm) -> ClassForm) = ui.update { local ->
        // Clearing the problems on every edit, so a marked field stops being marked as soon as it
        // is touched rather than only after another save attempt.
        local.copy(form = local.form?.let { transform(it).copy(problems = emptySet()) })
    }

    fun onSubmitForm() {
        val form = state.value.form ?: return
        if (!state.value.mayManage || !form.canSubmit) return
        ui.update { it.copy(form = form.copy(isSubmitting = true)) }

        scope.launch {
            val gymClass = GymClass(
                id = form.editing ?: GymClassId(newId()),
                name = form.name,
                category = form.category,
                room = form.room,
                // An unparseable capacity becomes 0, which the repository rejects with
                // CAPACITY_OUT_OF_RANGE. Deliberately not defaulted to something plausible: a
                // silent 20 would be a number nobody chose.
                capacity = form.capacity.trim().toIntOrNull() ?: 0,
                instructorStaffId = form.instructorStaffId,
                dayOfWeek = form.dayOfWeek,
                startsAt = LocalTime(form.startHour, form.startMinute),
                durationMinutes = form.durationMinutes,
            )

            when (val result = repository.save(gymClass)) {
                is AppResult.Failure -> ui.update {
                    it.copy(
                        form = it.form?.copy(isSubmitting = false),
                        notice = ClassesNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    is SaveClassOutcome.Invalid -> ui.update {
                        // Form stays open with the fields marked. Closing it would throw away
                        // everything typed.
                        it.copy(
                            form = it.form?.copy(isSubmitting = false, problems = outcome.problems),
                        )
                    }

                    SaveClassOutcome.Saved -> ui.update {
                        it.copy(form = null, notice = ClassesNotice.Saved(gymClass.name.trim()))
                    }

                    is SaveClassOutcome.SavedWithRoomClash -> ui.update {
                        // Saved, so the form closes — but the clash is named, because a shared
                        // room is legitimate and only staff can judge it.
                        it.copy(
                            form = null,
                            notice = ClassesNotice.RoomClash(
                                room = gymClass.room.trim(),
                                otherClassName = outcome.clashing.first().name,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun onDeleteClass(id: GymClassId) {
        if (!state.value.mayManage) return
        val target = state.value.timetable.classes.firstOrNull { it.id == id } ?: return
        scope.launch {
            when (val result = repository.delete(id)) {
                is AppResult.Failure ->
                    ui.update { it.copy(notice = ClassesNotice.Failed(result.error.message)) }

                is AppResult.Success ->
                    ui.update { it.copy(form = null, notice = ClassesNotice.Deleted(target.name)) }
            }
        }
    }

    /**
     * [selectedDay] is nullable here and non-null in [ClassesState]: null means "whatever today
     * is", which cannot be stored as a day without going stale at midnight.
     */
    private data class UiState(
        val selectedDay: DayOfWeek? = null,
        val form: ClassForm? = null,
        val notice: ClassesNotice? = null,
        val instructorFilter: String? = null,
        val roomFilter: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
