package com.anfas.feature.classes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasBanner
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasEdgeDivider
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.BannerTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.asLtrIsolate
import com.anfas.core.i18n.strings
import com.anfas.core.model.ClassBlock
import com.anfas.core.model.ClassCategory
import com.anfas.core.model.ClassSchedule
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import com.anfas.core.model.minuteOfDayToTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * The class timetable: `class-schedule` on a phone, `weekly-class-schedule` on desktop.
 *
 * The breakpoint picks between a day list and the week grid, as everywhere else in this app.
 * Desktop gets the grid because that is the screen the design draws for it and because a week is
 * what you plan against; a phone gets the day, because a 7-column grid on 448dp is unreadable and
 * the question at the desk is "what is on now".
 *
 * **The design's "14/20" and its capacity bars are not here.** Both need a booking system and
 * there is none — members do not sign in to this app at all, so nothing can know that fourteen
 * places are taken. What is shown is the limit, which is true and is the number the desk needs
 * when somebody asks whether there is room. See `ClassOccupancy`.
 */
@Composable
fun ClassesScreen(component: ClassesComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= GRID_MIN_WIDTH
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AnfasTheme.spacing.marginMobile)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnfasScreenHeader(
                title = if (wide) s.classes.weekTitle else s.classes.todayTitle,
                subtitle = s.classes.subtitle,
                actions = if (state.mayManage) {
                    {
                        AnfasPrimaryButton(
                            text = s.classes.addClass,
                            icon = AnfasIcons.Add,
                            onClick = component::onAddClass,
                        )
                    }
                } else {
                    null
                },
            )

            state.notice?.let { notice ->
                AnfasBanner(
                    message = notice.render(s),
                    icon = when (notice) {
                        is ClassesNotice.Failed -> AnfasIcons.ErrorOutline
                        is ClassesNotice.RoomClash -> AnfasIcons.Warning
                        else -> AnfasIcons.Check
                    },
                    tone = when (notice) {
                        is ClassesNotice.Failed -> BannerTone.Critical

                        // A shared room is legitimate, so this warns rather than alarms.
                        is ClassesNotice.RoomClash -> BannerTone.Warning

                        else -> BannerTone.Informational
                    },
                    dismissLabel = s.common.dismiss,
                    onDismiss = component::onNoticeShown,
                )
            }

            when (val content = state.content) {
                ClassesContent.Loading -> Box(Modifier.fillMaxSize())

                is ClassesContent.Failed -> AnfasEmptyState(
                    icon = AnfasIcons.ErrorOutline,
                    title = s.classes.loadFailedTitle,
                    message = content.message,
                )

                is ClassesContent.Loaded -> if (content.timetable.isEmpty) {
                    AnfasEmptyState(
                        icon = AnfasIcons.Schedule,
                        title = s.classes.emptyTitle,
                        message = s.classes.emptyMessage,
                    )
                } else if (wide) {
                    WeekGrid(state = state, component = component, s = s)
                } else {
                    DayView(state = state, component = component, s = s)
                }
            }
        }
    }

    state.form?.let { form ->
        ClassFormDialog(form = form, component = component, s = s)
    }
}

// ---------------------------------------------------------------------------------------------
// Compact: one day at a time.
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.DayView(state: ClassesState, component: ClassesComponent, s: AppStrings) {
    val split = ClassSchedule.splitByProgress(
        classes = state.visibleClasses,
        day = state.selectedDay,
        nowMinuteOfDay = state.nowMinuteOfDay,
    )

    DayPicker(
        selected = state.selectedDay,
        today = state.today,
        onSelect = component::onDaySelected,
        s = s,
    )

    if (split.isEmpty) {
        AnfasEmptyState(
            icon = AnfasIcons.Schedule,
            title = s.classes.emptyDayTitle(s.common.dayName(state.selectedDay.isoDayNumber)),
            message = s.classes.emptyDayMessage,
        )
        return
    }

    // weight(1f) so the list is bounded and scrolls. Without it the last row is clipped to
    // whatever is left over -- the defect the check-in log had.
    AnfasCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = split.upcoming, key = { it.id.value }) { item ->
                ClassRow(
                    gymClass = item,
                    instructor = state.timetable.instructorFor(item),
                    isNow = item.startMinute <= state.nowMinuteOfDay &&
                        state.selectedDay == state.today,
                    dimmed = false,
                    onClick = { component.onEditClass(item.id) }.takeIf { state.mayManage },
                    s = s,
                )
                AnfasTableDivider()
            }
            if (split.finished.isNotEmpty()) {
                item {
                    // Kept, not hidden: "did the 07:00 run?" is a question staff get asked, and
                    // a list that silently drops the morning cannot answer it.
                    Text(
                        text = s.classes.finishedToday,
                        style = AnfasTheme.textStyles.labelCaps,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                items(items = split.finished, key = { "done-${it.id.value}" }) { item ->
                    ClassRow(
                        gymClass = item,
                        instructor = state.timetable.instructorFor(item),
                        isNow = false,
                        dimmed = true,
                        onClick = { component.onEditClass(item.id) }.takeIf { state.mayManage },
                        s = s,
                    )
                    AnfasTableDivider()
                }
            }
        }
    }
}

@Composable
private fun DayPicker(
    selected: DayOfWeek,
    today: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    s: AppStrings,
) {
    Row(
        // Scrolls: seven day chips do not fit on a narrow phone, and wrapping them to two rows
        // pushes the timetable down.
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClassSchedule.weekOrder(today).forEach { day ->
            AnfasChoiceChip(
                label = s.common.dayNameShort(day.isoDayNumber),
                selected = day == selected,
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun ClassRow(
    gymClass: GymClass,
    instructor: String?,
    isNow: Boolean,
    dimmed: Boolean,
    onClick: (() -> Unit)?,
    s: AppStrings,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = gymClass.category.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The design's leading category stripe. First child of a Row, so it mirrors for free.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(AnfasShapes.chip)
                .background(if (dimmed) accent.copy(alpha = 0.35f) else accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gymClass.name,
                style = AnfasTheme.textStyles.bodyMedium,
                color = if (dimmed) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = instructor ?: s.classes.unassigned,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                // A clock time is a Latin/numeric run, so it is isolated rather than pushed
                // through dataMonoLtr -- the surrounding paragraph is Arabic and the colon is
                // direction-neutral, which would otherwise migrate to the wrong end.
                text = timeRange(gymClass).asLtrIsolate(),
                style = AnfasTheme.textStyles.dataMono,
                color = if (dimmed) scheme.onSurfaceVariant else scheme.onSurface,
            )
            Text(
                text = if (isNow) s.classes.inProgress else gymClass.room,
                style = AnfasTheme.textStyles.labelCaps,
                color = if (isNow) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wide: the week grid, which is the design's primary view of this feature.
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.WeekGrid(state: ClassesState, component: ClassesComponent, s: AppStrings) {
    val classes = state.visibleClasses
    val startHour = ClassSchedule.gridStartHour(classes)
    val endHour = ClassSchedule.gridEndHour(classes)
    val hours = (endHour - startHour).coerceAtLeast(1)
    val days = ClassSchedule.weekOrder(state.today)

    ClassFilters(state = state, component = component, s = s)

    // Hoisted rather than written inline per column: inside takeIf the lambda has no inferable
    // parameter type, and repeating it seven times would allocate seven identical lambdas.
    val onClassClick: ((GymClassId) -> Unit)? =
        if (state.mayManage) component::onEditClass else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // Vertical scroll on the grid rather than the page: the day headers stay put while
            // the hours move, which is the whole point of a timetable.
            .verticalScroll(rememberScrollState()),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // The hour axis. Fixed width so every day column gets the same share of what is left.
            Column(modifier = Modifier.width(HOUR_AXIS_WIDTH)) {
                Spacer(Modifier.height(DAY_HEADER_HEIGHT))
                repeat(hours) { index ->
                    Box(modifier = Modifier.fillMaxWidth().height(HOUR_HEIGHT)) {
                        Text(
                            text = minuteOfDayToTime((startHour + index) * 60)
                                .toString()
                                .asLtrIsolate(),
                            style = AnfasTheme.textStyles.labelCaps,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            days.forEach { day ->
                DayColumn(
                    day = day,
                    isToday = day == state.today,
                    blocks = ClassSchedule.layoutDay(classes, day),
                    startHour = startHour,
                    hours = hours,
                    nowMinuteOfDay = state.nowMinuteOfDay.takeIf { day == state.today },
                    instructorFor = state.timetable::instructorFor,
                    onClassClick = onClassClick,
                    s = s,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Legend(s = s)
    }
}

@Composable
private fun DayColumn(
    day: DayOfWeek,
    isToday: Boolean,
    blocks: List<ClassBlock>,
    startHour: Int,
    hours: Int,
    nowMinuteOfDay: Int?,
    instructorFor: (gymClass: GymClass) -> String?,
    onClassClick: ((GymClassId) -> Unit)?,
    s: AppStrings,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DAY_HEADER_HEIGHT)
                .background(if (isToday) scheme.primary.copy(alpha = 0.10f) else scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = s.common.dayNameShort(day.isoDayNumber),
                style = AnfasTheme.textStyles.labelCaps,
                color = if (isToday) scheme.primary else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(HOUR_HEIGHT * hours)
                .background(if (isToday) scheme.primary.copy(alpha = 0.04f) else scheme.surface),
        ) {
            val columnWidth = maxWidth
            // Hour rules, drawn under the blocks.
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(hours) {
                    Box(modifier = Modifier.fillMaxWidth().height(HOUR_HEIGHT)) {
                        AnfasTableDivider()
                    }
                }
            }
            // The current-time line, only on today.
            if (nowMinuteOfDay != null) {
                val offset = minuteOffset(nowMinuteOfDay, startHour)
                if (offset >= 0.dp && offset <= HOUR_HEIGHT * hours) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = offset)
                            .height(2.dp)
                            .background(scheme.primary),
                    )
                }
            }
            blocks.forEach { block ->
                val laneWidth = columnWidth / block.laneCount
                ClassBlockCard(
                    block = block,
                    instructor = instructorFor(block.gymClass),
                    onClick = onClassClick?.let { click -> { click(block.gymClass.id) } },
                    s = s,
                    modifier = Modifier
                        .width(laneWidth)
                        // offset(x =) is direction-aware, so lanes mirror in Arabic without
                        // the caller doing anything -- which is correct here, unlike over a
                        // photograph.
                        .offset(
                            x = laneWidth * block.lane,
                            y = minuteOffset(block.gymClass.startMinute, startHour),
                        )
                        .height(blockHeight(block.gymClass)),
                )
            }
        }
    }
}

@Composable
private fun ClassBlockCard(
    block: ClassBlock,
    instructor: String?,
    onClick: (() -> Unit)?,
    s: AppStrings,
    modifier: Modifier = Modifier,
) {
    val accent = block.gymClass.category.accent()
    Column(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            text = block.gymClass.name,
            style = AnfasTheme.textStyles.labelCaps,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = instructor ?: s.classes.unassigned,
            style = AnfasTheme.textStyles.labelCaps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The limit, not an occupancy. There is nothing that could fill a "14/20".
        Text(
            text = s.classes.places(block.gymClass.capacity),
            style = AnfasTheme.textStyles.labelCaps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClassFilters(state: ClassesState, component: ClassesComponent, s: AppStrings) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnfasChoiceChip(
            label = s.classes.filterAllCoaches,
            selected = state.instructorFilter == null,
            onClick = { component.onInstructorFilterChanged(null) },
        )
        state.instructors.forEach { (id, name) ->
            AnfasChoiceChip(
                label = name,
                selected = state.instructorFilter == id,
                onClick = { component.onInstructorFilterChanged(id) },
            )
        }
        Spacer(Modifier.width(8.dp))
        AnfasChoiceChip(
            label = s.classes.filterAllRooms,
            selected = state.roomFilter == null,
            onClick = { component.onRoomFilterChanged(null) },
        )
        state.rooms.forEach { room ->
            AnfasChoiceChip(
                label = room,
                selected = state.roomFilter == room,
                onClick = { component.onRoomFilterChanged(room) },
            )
        }
    }
}

/** The design's legend. Worth keeping: the block colours are the only thing carrying category. */
@Composable
private fun Legend(s: AppStrings) {
    AnfasEdgeDivider()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClassCategory.entries.forEach { category ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(AnfasShapes.chip)
                        .background(category.accent()),
                )
                Text(
                    text = category.label(s),
                    style = AnfasTheme.textStyles.labelCaps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun timeRange(gymClass: GymClass): String = "${gymClass.startsAt} – ${gymClass.endsAt}"

private fun minuteOffset(minuteOfDay: Int, startHour: Int) =
    HOUR_HEIGHT * ((minuteOfDay - startHour * 60) / 60f)

private fun blockHeight(gymClass: GymClass) =
    (HOUR_HEIGHT * (gymClass.endMinute - gymClass.startMinute) / 60f)
        .coerceAtLeast(MIN_BLOCK_HEIGHT)

internal fun ClassesNotice.render(s: AppStrings): String = when (this) {
    is ClassesNotice.Saved -> s.classes.saved(name)
    is ClassesNotice.Deleted -> s.classes.deleted(name)
    is ClassesNotice.RoomClash -> s.classes.roomClash(room, otherClassName)
    is ClassesNotice.Failed -> message
}

/** The design's grid is 80px an hour. */
private val HOUR_HEIGHT = 80.dp
private val DAY_HEADER_HEIGHT = 40.dp
private val HOUR_AXIS_WIDTH = 56.dp

/** A 15-minute class would otherwise be 20dp tall and hold no legible text. */
private val MIN_BLOCK_HEIGHT = 34.dp

/**
 * The content width the week grid needs, measured from what it has to draw rather than copied
 * from `AnfasBreakpoints.tabletMax`.
 *
 * Using the 1024dp window breakpoint here was a bug, found by running this on an iPad Pro 13":
 * that constant decides **rail versus bottom bar**, and it is measured against the *window*. The
 * screen only ever receives the window minus the 256dp rail, so on a 1032pt iPad the grid was
 * unreachable — the day view showed on a tablet in landscape, and on a desktop window under
 * 1280dp too.
 *
 * The real requirement is the hour axis plus seven columns wide enough for a class name, a coach
 * and a capacity: 56dp + 7 x 100dp. Below that the grid is worse than the list, which is exactly
 * when it should not be used.
 */
private val GRID_MIN_WIDTH = (56 + 7 * 100).dp
