package com.anfas.core.model

/**
 * Allocates the human-facing membership number.
 *
 * Pure, and shared by both things that create members — OCR intake and the manual add form. It
 * was private to the intake repository, which meant a member added by hand would have had a
 * different numbering rule from one imported off a sheet, and the two could collide.
 *
 * This is a **placeholder for a real numbering policy**: the export shows numbers around 88xxx
 * with no stated scheme, and prefixes, check digits or per-branch ranges are a business decision
 * rather than something to invent here. What it does guarantee is that a new number is never one
 * already issued, which is what the unique index on `members.membership_number` requires.
 */
object MembershipNumbers {
    const val FIRST: Int = 10_000

    private const val PREFIX = "#"

    /**
     * One past the highest number currently issued.
     *
     * Non-numeric and malformed values are ignored rather than rejected: a number typed by hand
     * during a migration should not be able to stop the gym registering anybody.
     */
    fun next(existing: Collection<String>): String = format(nextValue(existing))

    /**
     * A run of [count] consecutive numbers, for a bulk import. Allocated in one pass so a batch
     * cannot hand two rows the same number — which it did once, when a caller reused a constant.
     */
    fun nextRun(existing: Collection<String>, count: Int): List<String> {
        require(count >= 0) { "count must not be negative" }
        val start = nextValue(existing)
        return (0 until count).map { format(start + it) }
    }

    fun format(value: Int): String = "$PREFIX$value"

    /** Digits only, so "#10000" and "10000" are the same number. */
    fun parse(number: String): Int? = number.filter(Char::isDigit).toIntOrNull()

    /**
     * Never below [FIRST], even when every existing number is.
     *
     * A single stray low number — legacy data, or something typed by hand during a migration —
     * would otherwise drag the whole sequence down: with one member at `#1`, the next member
     * would be `#2` rather than `#10000`. [FIRST] is where numbering *starts*, so numbers below
     * it are treated as history rather than as the current sequence. Numbers above it continue
     * normally.
     */
    private fun nextValue(existing: Collection<String>): Int {
        val highest = existing.mapNotNull(::parse).maxOrNull() ?: (FIRST - 1)
        return maxOf(FIRST, highest + 1)
    }
}
