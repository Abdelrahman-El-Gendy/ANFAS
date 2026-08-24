package com.anfas.feature.classes

import com.anfas.core.data.ClassProblem
import com.anfas.core.data.Timetable
import com.anfas.core.model.ClassCategory
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import kotlinx.datetime.DayOfWeek

sealed interface ClassesContent {
    data object Loading : ClassesContent

    /**
     * The whole week, even on the day view. "Nothing on Tuesday" and "no timetable at all" are
     * different screens, and only the full week can tell them apart.
     */
    data class Loaded(val timetable: Timetable) : ClassesContent

    data class Failed(val message: String) : ClassesContent
}

/**
 * [selectedDay] drives the mobile day view and the "today" highlight on the week grid. Held in
 * state rather than derived from the clock on every recomposition, so a staff member can look at
 * Thursday's classes without the screen snapping back.
 *
 * [nowMinuteOfDay] is the clock, sampled by the component. In state because both screens use it —
 * the day view splits finished from upcoming, and the grid draws its current-time line — and
 * because passing it in is what makes those pure functions testable.
 */
data class ClassesState(
    val content: ClassesContent = ClassesContent.Loading,
    val selectedDay: DayOfWeek = DayOfWeek.MONDAY,
    val today: DayOfWeek = DayOfWeek.MONDAY,
    val nowMinuteOfDay: Int = 0,
    val form: ClassForm? = null,
    val notice: ClassesNotice? = null,
    /** Whether this session holds `Permission.MANAGE_CLASSES`. */
    val mayManage: Boolean = false,
    /** Filters from the design's two selects. Null means "all". */
    val instructorFilter: String? = null,
    val roomFilter: String? = null,
) {
    val timetable: Timetable get() = (content as? ClassesContent.Loaded)?.timetable
        ?: Timetable.EMPTY

    /**
     * The classes the screens actually draw. Filtering here rather than in each screen so the day
     * view and the grid can never disagree about what a filter means.
     */
    val visibleClasses: List<GymClass>
        get() = timetable.classes
            .filter { instructorFilter == null || it.instructorStaffId == instructorFilter }
            .filter { roomFilter == null || it.room == roomFilter }

    /** Distinct rooms currently in use, for the room filter. Derived, never stored. */
    val rooms: List<String>
        get() = timetable.classes.map { it.room }.distinct().sorted()

    /**
     * Staff who teach at least one class, for the coach filter.
     *
     * Derived from the timetable rather than from the staff list on purpose: a filter offering a
     * coach with no classes can only ever produce an empty grid.
     */
    val instructors: List<Pair<String, String>>
        get() = timetable.classes.mapNotNull { it.instructorStaffId }
            .distinct()
            .mapNotNull { id -> timetable.instructorNames[id]?.let { id to it } }
            .sortedBy { it.second }

    val isFiltered: Boolean get() = instructorFilter != null || roomFilter != null
}

/**
 * The add/edit form.
 *
 * Held as state rather than remembered inside the dialog so a rotation does not lose a
 * half-entered class, exactly as `AddMemberForm` does. Text fields are strings, not parsed
 * numbers: a capacity box that refuses to hold "" cannot be cleared and retyped.
 */
data class ClassForm(
    /** Null when adding. Set when editing, and what makes save an update rather than an insert. */
    val editing: GymClassId? = null,
    val name: String = "",
    val category: ClassCategory = ClassCategory.GENERAL,
    val room: String = "",
    val capacity: String = "",
    val instructorStaffId: String? = null,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startHour: Int = 18,
    val startMinute: Int = 0,
    val durationMinutes: Int = 60,
    val isSubmitting: Boolean = false,
    /** Set after a rejected save, so the form can mark the offending fields. */
    val problems: Set<ClassProblem> = emptySet(),
    /** Coaches offered in the picker: id to display name. */
    val instructorOptions: List<Pair<String, String>> = emptyList(),
) {
    val isEditing: Boolean get() = editing != null

    /**
     * Enabled purely on "there is something to submit". The real validation lives in the
     * repository and reports per-field problems — duplicating the rules here is how a form ends
     * up disagreeing with the thing that saves it.
     */
    val canSubmit: Boolean get() = !isSubmitting && name.isNotBlank()
}

/** Typed like every other notice here: the screen owns the wording. */
sealed interface ClassesNotice {
    data class Saved(val name: String) : ClassesNotice
    data class Deleted(val name: String) : ClassesNotice
    data class RoomClash(val room: String, val otherClassName: String) : ClassesNotice
    data class Failed(val message: String) : ClassesNotice
}
