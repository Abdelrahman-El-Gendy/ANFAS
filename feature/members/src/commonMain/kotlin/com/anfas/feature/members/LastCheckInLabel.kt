package com.anfas.feature.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.anfas.core.common.RelativeTime
import com.anfas.core.i18n.format
import com.anfas.core.i18n.strings
import com.anfas.core.model.Member
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * The directory's "Last check-in" cell.
 *
 * The rules live in [RelativeTime] (shared with the reminder queue) and the wording in
 * `AppStrings`; this is only the members-specific choice of a comma between day and time.
 */
@Composable
internal fun Member.lastCheckInLabel(): String {
    val s = strings
    // Keyed on the instant so the clock is read once per value rather than on every recomposition.
    val stamp = remember(lastCheckInAt) {
        RelativeTime.classify(lastCheckInAt, Clock.System.now(), TimeZone.currentSystemDefault())
    }
    return s.format(stamp, separator = ", ")
}
