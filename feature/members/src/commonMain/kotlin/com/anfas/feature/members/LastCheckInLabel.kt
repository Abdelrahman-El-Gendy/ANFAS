package com.anfas.feature.members

import com.anfas.core.common.RelativeTime
import com.anfas.core.model.Member
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * The directory's "Last check-in" cell. The formatting rules live in
 * [com.anfas.core.common.RelativeTime] because the reminder queue needs them too; this is
 * just the members-specific wording choice — a comma between the day and the time.
 */
internal fun Member.lastCheckInLabel(
    now: kotlin.time.Instant = Clock.System.now(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = RelativeTime.format(
    instant = lastCheckInAt,
    now = now,
    zone = zone,
    separator = ", ",
)
