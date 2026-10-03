package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.database.GymClassDao
import com.anfas.core.database.GymClassEntity
import com.anfas.core.database.StaffDao
import com.anfas.core.database.SyncTables
import com.anfas.core.model.ClassCategory
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import com.anfas.core.model.minuteOfDayToTime
import com.anfas.core.model.toMinuteOfDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlin.uuid.Uuid

internal class OfflineFirstClassRepository(
    private val classes: GymClassDao,
    private val staff: StaffDao,
    private val dispatchers: AppDispatchers,
) : ClassRepository {

    /**
     * The timetable joined with staff names in one flow, so the grid never renders a slot before
     * it knows whose it is — two separate flows would show every class as unassigned for a frame
     * and then repaint.
     *
     * Only `Role.Coach` holders are *offered* in the form, but every staff row is resolved here:
     * an owner can teach, and a class whose coach was later promoted must not lose its name.
     */
    override fun observeTimetable(): Flow<AppResult<Timetable>> =
        combine(classes.observeAll(), staff.observeAll()) { rows, staffRows ->
            Timetable(
                classes = rows.map { it.toDomain() },
                instructorNames = staffRows.associate { it.id to it.displayName },
            )
        }.asAppResult("Could not load the class timetable") { it }

    override suspend fun save(gymClass: GymClass): AppResult<SaveClassOutcome> =
        withContext(dispatchers.io) {
            runStorage("Could not save the class") {
                val problems = validate(gymClass)
                if (problems.isNotEmpty()) {
                    return@runStorage SaveClassOutcome.Invalid(problems)
                }

                classes.upsertTracked(
                    gymClass = gymClass.toEntity(),
                    change = changeFor(SyncTables.SCHEDULED_CLASSES, gymClass.id.value),
                )

                // Read back *after* writing rather than before, so the check sees the row as
                // saved and cannot report a clash with the previous version of the class being
                // edited. Excluding by id would miss the case where the id itself changed.
                val sameRoom = classes.findClashesInRoom(
                    room = gymClass.room,
                    dayOfWeek = gymClass.dayOfWeek.isoDayNumber,
                    excludeId = gymClass.id.value,
                ).map { it.toDomain() }.filter { it.overlaps(gymClass) }

                if (sameRoom.isEmpty()) {
                    SaveClassOutcome.Saved
                } else {
                    SaveClassOutcome.SavedWithRoomClash(sameRoom)
                }
            }
        }

    override suspend fun delete(id: GymClassId): AppResult<Unit> = withContext(dispatchers.io) {
        runStorage("Could not delete the class") {
            classes.deleteTracked(id = id.value, nowEpochMs = capturedAt())
        }
    }

    private fun validate(gymClass: GymClass): Set<ClassProblem> = buildSet {
        if (gymClass.name.isBlank()) add(ClassProblem.NAME_BLANK)
        if (gymClass.room.isBlank()) add(ClassProblem.ROOM_BLANK)
        if (gymClass.capacity !in GymClass.MIN_CAPACITY..GymClass.MAX_CAPACITY) {
            add(ClassProblem.CAPACITY_OUT_OF_RANGE)
        }
        if (gymClass.durationMinutes !in
            GymClass.MIN_DURATION_MINUTES..GymClass.MAX_DURATION_MINUTES
        ) {
            add(ClassProblem.DURATION_OUT_OF_RANGE)
        }
    }

    companion object {
        /** So the caller does not have to know how ids are minted. */
        fun newId(): GymClassId = GymClassId(Uuid.random().toString())
    }
}

private fun GymClassEntity.toDomain() = GymClass(
    id = GymClassId(id),
    name = name,
    // An unrecognised stored category reads as GENERAL rather than throwing. The alternative is
    // a timetable that fails to load entirely because one row has a value from a newer build.
    category = ClassCategory.entries.firstOrNull { it.name == category } ?: ClassCategory.GENERAL,
    room = room,
    capacity = capacity,
    instructorStaffId = instructorStaffId,
    dayOfWeek = DayOfWeek.entries.firstOrNull { it.isoDayNumber == dayOfWeek }
        ?: DayOfWeek.MONDAY,
    startsAt = minuteOfDayToTime(startMinuteOfDay.coerceIn(0, MAX_MINUTE_OF_DAY)),
    durationMinutes = durationMinutes,
)

private fun GymClass.toEntity() = GymClassEntity(
    id = id.value,
    name = name.trim(),
    category = category.name,
    room = room.trim(),
    capacity = capacity,
    instructorStaffId = instructorStaffId,
    dayOfWeek = dayOfWeek.isoDayNumber,
    startMinuteOfDay = startsAt.toMinuteOfDay(),
    durationMinutes = durationMinutes,
)

private const val MAX_MINUTE_OF_DAY = 24 * 60 - 1
