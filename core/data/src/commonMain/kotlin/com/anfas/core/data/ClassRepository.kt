package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import kotlinx.coroutines.flow.Flow

/**
 * The weekly class timetable.
 *
 * [observeTimetable] returns the whole week rather than a day at a time. Both designed screens
 * need it: the desktop grid draws seven days at once, and the mobile screen has to know that
 * "nothing today" is different from "nothing at all" — a gym with an empty Tuesday and a gym with
 * no timetable want completely different empty states.
 */
interface ClassRepository {

    fun observeTimetable(): Flow<AppResult<Timetable>>

    /**
     * Creates or replaces a slot. Validated first, so an unusable row can never reach the table.
     *
     * Returns [SaveClassOutcome] rather than throwing on a clash: a class overlapping another in
     * the same room is a question for the person entering it, not an error.
     */
    suspend fun save(gymClass: GymClass): AppResult<SaveClassOutcome>

    suspend fun delete(id: GymClassId): AppResult<Unit>
}

/**
 * The timetable plus the instructor names it refers to.
 *
 * Names are resolved here rather than in the UI so a screen never holds a staff id it has to look
 * up, and are a map rather than fields on [GymClass] because one coach teaches many slots — the
 * domain type stays a pure timetable row.
 *
 * A missing entry means unassigned, which happens both when no coach is set and when the id no
 * longer resolves. The UI cannot tell those apart and should not: either way, that slot needs
 * somebody.
 */
data class Timetable(val classes: List<GymClass>, val instructorNames: Map<String, String>) {
    fun instructorFor(gymClass: GymClass): String? =
        gymClass.instructorStaffId?.let { instructorNames[it] }

    val isEmpty: Boolean get() = classes.isEmpty()

    companion object {
        val EMPTY = Timetable(classes = emptyList(), instructorNames = emptyMap())
    }
}

/**
 * What a save did.
 *
 * [Clashes] is a **warning, not a rejection** — the row is saved. Two classes can legitimately
 * share a room when one is a small group in the corner, and refusing would make the timetable
 * impossible to enter for a gym that does that. What staff need is to be told.
 */
sealed interface SaveClassOutcome {
    data object Saved : SaveClassOutcome

    /** Saved, but it overlaps these in the same room. */
    data class SavedWithRoomClash(val clashing: List<GymClass>) : SaveClassOutcome

    /** Not saved. The field-level problems, so the form can mark them. */
    data class Invalid(val problems: Set<ClassProblem>) : SaveClassOutcome
}

/** Why a class could not be saved. Typed, so the screen owns the wording as everywhere else. */
enum class ClassProblem {
    NAME_BLANK,
    ROOM_BLANK,
    CAPACITY_OUT_OF_RANGE,
    DURATION_OUT_OF_RANGE,
}
