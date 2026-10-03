package com.anfas.core.data

import com.anfas.core.database.MemberEntity
import com.anfas.core.model.IntakeValidator
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.TemplateLanguage
import kotlin.time.Instant

/**
 * Storage <-> domain. Deliberately total in one direction only: any row whose [status] string
 * is unrecognised maps to [MembershipStatus.PAUSED] rather than throwing, so one bad row can
 * never take down the whole directory. A crash here would be a worse outcome than a member
 * shown in the wrong state.
 */
internal fun MemberEntity.toDomain(): Member = Member(
    id = MemberId(id),
    fullName = fullName,
    membershipNumber = membershipNumber,
    phone = phone,
    status = MembershipStatus.entries.firstOrNull { it.name == status } ?: MembershipStatus.PAUSED,
    lastCheckInAt = lastCheckInAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
    avatarUrl = avatarUrl,
    whatsappOptIn = whatsappOptIn,
    // Null on an unreadable value as well as on an absent one, and both mean the same thing to
    // the scheduler: not asked, so fall back to Arabic. Degrading rather than throwing for the
    // same reason `status` does above.
    preferredLanguage = preferredLanguage?.let { stored ->
        TemplateLanguage.entries.firstOrNull { it.name == stored }
    },
)

/**
 * The normalised phone column is derived here, in the one place every write goes through, so
 * it can never drift from [Member.phone]. [IntakeValidator.normalisePhone] is reused rather
 * than reimplemented — intake's duplicate check compares against this column, and two
 * different normalisations would silently stop matching.
 */
internal fun Member.toEntity(): MemberEntity = MemberEntity(
    id = id.value,
    fullName = fullName,
    membershipNumber = membershipNumber,
    phone = phone,
    phoneNormalised = phone
        ?.let(IntakeValidator::normalisePhone)
        ?.takeIf { it.isNotEmpty() },
    status = status.name,
    lastCheckInAtEpochMs = lastCheckInAt?.toEpochMilliseconds(),
    avatarUrl = avatarUrl,
    whatsappOptIn = whatsappOptIn,
    preferredLanguage = preferredLanguage?.name,
)
