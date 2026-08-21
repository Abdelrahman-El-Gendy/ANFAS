package com.anfas.core.model

import kotlinx.datetime.LocalDate

/**
 * Decides what is wrong with each row of a batch — the logic behind the export's
 * "6 of 8 rows ready for import" counter.
 *
 * Pure, and revalidates the **whole batch** rather than one row at a time, because two of the
 * checks are relational: a phone number can only be a duplicate with respect to the other rows
 * and to the existing membership. Editing one row can therefore clear or create an issue on a
 * different row, and a per-row validator would miss that.
 */
object IntakeValidator {

    /**
     * @param existingPhones phone numbers already in the membership, normalised by the caller
     *   with [normalisePhone].
     */
    fun validate(rows: List<IntakeRow>, existingPhones: Set<String>): List<IntakeRow> {
        // Count within the batch first, so both rows of a duplicated pair get flagged rather
        // than arbitrarily blaming the second one.
        val phoneCounts = rows
            .map { normalisePhone(it.phone.value) }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()

        return rows.map { row -> row.copy(issues = issuesFor(row, phoneCounts, existingPhones)) }
    }

    private fun issuesFor(
        row: IntakeRow,
        phoneCounts: Map<String, Int>,
        existingPhones: Set<String>,
    ): Set<IntakeIssue> {
        val issues = mutableSetOf<IntakeIssue>()

        if (row.name.isBlank) issues += IntakeIssue.MISSING_NAME

        val phone = normalisePhone(row.phone.value)
        when {
            phone.isEmpty() -> issues += IntakeIssue.MISSING_PHONE
            phone in existingPhones -> issues += IntakeIssue.DUPLICATE_PHONE
            (phoneCounts[phone] ?: 0) > 1 -> issues += IntakeIssue.DUPLICATE_IN_BATCH
        }

        val start = parseDate(row.startDate.value)
        val end = parseDate(row.endDate.value)
        if (row.startDate.value.isNotBlank() && start == null) issues += IntakeIssue.UNREADABLE_DATE
        if (row.endDate.value.isNotBlank() && end == null) issues += IntakeIssue.UNREADABLE_DATE
        // Only comparable when both parsed. An unreadable date is its own, milder problem.
        if (start != null && end != null && end < start) issues += IntakeIssue.END_BEFORE_START

        if (row.plan.value.isNotBlank() && parsePlanLabel(row.plan.value) == null) {
            issues += IntakeIssue.UNKNOWN_PLAN
        }

        // A row whose cells are individually plausible but collectively shaky still deserves a
        // glance; the marker is advisory so it never blocks the import.
        if (row.fields.any { it.needsReview }) issues += IntakeIssue.LOW_CONFIDENCE

        return issues
    }

    /**
     * Strips everything except digits, and drops an Egyptian country code so that
     * "+20 100 123 4567", "0100 123 4567" and "01001234567" are recognised as one number.
     * Without this, duplicate detection misses the common case of a member writing their
     * number differently on a second sheet.
     */
    fun normalisePhone(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            digits.startsWith(EGYPT_COUNTRY_CODE) -> "0" + digits.removePrefix(EGYPT_COUNTRY_CODE)
            else -> digits
        }
    }

    /**
     * Parses the date formats the sheets actually contain. Returns null rather than throwing —
     * an unparseable date is a review item, not a crash.
     *
     * Accepts "Nov 1, 2023", "1 Nov 2023" and ISO "2023-11-01". Deliberately does **not**
     * accept bare numeric forms like "01/11/2023": there is no way to tell day-first from
     * month-first, and silently guessing would put members on the wrong plan dates.
     */
    fun parseDate(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        runCatching { return LocalDate.parse(text) }

        val cleaned = text.replace(",", " ").split(' ').filter { it.isNotBlank() }
        if (cleaned.size != 3) return null

        val monthFromName = { token: String ->
            MONTH_NAMES.indexOfFirst { it.equals(token.take(3), ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.plus(1)
        }

        // "Nov 1 2023"
        monthFromName(cleaned[0])?.let { month ->
            val day = cleaned[1].toIntOrNull() ?: return null
            val year = cleaned[2].toIntOrNull() ?: return null
            return runCatching { LocalDate(year, month, day) }.getOrNull()
        }
        // "1 Nov 2023"
        monthFromName(cleaned[1])?.let { month ->
            val day = cleaned[0].toIntOrNull() ?: return null
            val year = cleaned[2].toIntOrNull() ?: return null
            return runCatching { LocalDate(year, month, day) }.getOrNull()
        }
        return null
    }

    /**
     * Maps a plan cell to a known label. "Trial" appears on the sheets but is not a
     * [PlanTier] — it is not purchasable through the renewal sheet — so it is recognised here
     * as valid input without being forced into the tier enum.
     */
    fun parsePlanLabel(raw: String): String? =
        KNOWN_PLAN_LABELS.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }

    private const val EGYPT_COUNTRY_CODE = "20"

    private val MONTH_NAMES = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    /**
     * What the sheets are allowed to say. "Trial" is included because the export's
     * `ocr-intake-review` shows it, even though no purchasable plan of that name exists yet.
     */
    val KNOWN_PLAN_LABELS = listOf("Trial", "Monthly", "Quarterly", "Annual")
}
