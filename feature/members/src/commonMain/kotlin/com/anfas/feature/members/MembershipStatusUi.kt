package com.anfas.feature.members

import androidx.compose.runtime.Composable
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.strings
import com.anfas.core.model.MembershipStatus

/**
 * Domain -> presentation. Lives in the feature because :core:designsystem must not know about
 * domain types and :core:model must not know about the design system or about language.
 *
 * Tone follows the export: Active reads as sage/positive, Expired and Suspended as error, Paused
 * as a neutral surface step.
 */
internal val MembershipStatus.chipTone: ChipTone
    get() = when (this) {
        MembershipStatus.ACTIVE -> ChipTone.Positive
        MembershipStatus.EXPIRED -> ChipTone.Critical
        MembershipStatus.SUSPENDED -> ChipTone.Critical
        MembershipStatus.PAUSED -> ChipTone.Neutral
    }

@Composable
internal fun MembershipStatus.label(): String {
    val m = strings.members
    return when (this) {
        MembershipStatus.ACTIVE -> m.statusActive
        MembershipStatus.EXPIRED -> m.statusExpired
        MembershipStatus.SUSPENDED -> m.statusSuspended
        MembershipStatus.PAUSED -> m.statusPaused
    }
}
