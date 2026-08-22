package com.anfas.core.model

/**
 * Which fields of one member record disagree between this device and the server.
 *
 * Pure, and in `:core:model` rather than in the sync layer, because the *decision* about what
 * counts as a conflict is domain knowledge and outlives whatever transport eventually delivers
 * the remote copy. The export's `sync-conflict` screen renders exactly this list.
 *
 * There is no sync yet. This exists ahead of it because the conflict rules — which fields matter,
 * which resolve silently — are the part worth settling and testing while it is cheap, rather than
 * while a merge is dropping someone's phone number.
 */
data class MemberConflict(val local: Member, val remote: Member, val fields: List<FieldConflict>) {
    /**
     * True when the records differ in a way a human has to arbitrate. False means the two copies
     * are equivalent for every field that matters and either may be kept.
     */
    val needsResolution: Boolean get() = fields.any { it.differs }

    val differing: List<FieldConflict> get() = fields.filter { it.differs }

    companion object {
        /**
         * Compares only the fields a human would arbitrate.
         *
         * [Member.lastCheckInAt] is deliberately excluded: a check-in is an append-only event, so
         * the later timestamp is simply the truth and asking staff to choose would be noise.
         * [Member.avatarUrl] is excluded for the same reason — a newer photo is not a conflict.
         * [Member.id] is the join key; two records with different ids are not the same member.
         */
        fun of(local: Member, remote: Member): MemberConflict = MemberConflict(
            local = local,
            remote = remote,
            fields = listOf(
                FieldConflict(ConflictField.FULL_NAME, local.fullName, remote.fullName),
                FieldConflict(
                    ConflictField.MEMBERSHIP_NUMBER,
                    local.membershipNumber,
                    remote.membershipNumber,
                ),
                FieldConflict(ConflictField.PHONE, local.phone, remote.phone),
                FieldConflict(
                    ConflictField.STATUS,
                    local.status.name,
                    remote.status.name,
                ),
            ),
        )
    }
}

/**
 * One row of the comparison.
 *
 * [local] and [remote] are already-rendered strings rather than typed values because the screen
 * only displays them, and a `null` means "empty on this side" — which the design shows as
 * "(Empty)" and is itself a meaningful difference.
 */
data class FieldConflict(val field: ConflictField, val local: String?, val remote: String?) {
    /**
     * Blank and null are the same thing here. OCR intake can store an empty string where another
     * device stored null, and flagging that as a conflict a human must resolve would put an
     * unanswerable question in front of staff.
     */
    val differs: Boolean get() = local.orEmpty().trim() != remote.orEmpty().trim()
}

/**
 * The fields worth arbitrating. Names only — how each is labelled is the presentation layer's
 * business, so `:core:model` stays free of display copy.
 */
enum class ConflictField {
    FULL_NAME,
    MEMBERSHIP_NUMBER,
    PHONE,
    STATUS,
}
