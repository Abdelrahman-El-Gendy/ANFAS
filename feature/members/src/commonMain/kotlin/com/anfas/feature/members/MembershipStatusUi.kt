package com.anfas.feature.members

import com.anfas.core.designsystem.ChipTone
import com.anfas.core.model.MembershipStatus

/**
 * Domain -> presentation. This mapping lives in the feature because :core:designsystem must
 * not know about domain types, and :core:model must not know about the design system.
 *
 * Tones follow the export: Active reads as sage/positive, Expired and Suspended as error,
 * Paused as a neutral surface step.
 */
internal val MembershipStatus.chipTone: ChipTone
    get() = when (this) {
        MembershipStatus.ACTIVE -> ChipTone.Positive
        MembershipStatus.EXPIRED -> ChipTone.Critical
        MembershipStatus.SUSPENDED -> ChipTone.Critical
        MembershipStatus.PAUSED -> ChipTone.Neutral
    }

internal val MembershipStatus.label: String
    get() = when (this) {
        MembershipStatus.ACTIVE -> "Active"
        MembershipStatus.EXPIRED -> "Expired"
        MembershipStatus.SUSPENDED -> "Suspended"
        MembershipStatus.PAUSED -> "Paused"
    }
