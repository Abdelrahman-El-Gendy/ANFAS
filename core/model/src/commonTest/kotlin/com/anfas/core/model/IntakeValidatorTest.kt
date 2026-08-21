package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntakeValidatorTest {

    @Test
    fun `a clean row has no issues and is importable`() {
        val rows =
            validate(row(1, "Alex Thompson", "555-0192", "Nov 1, 2023", "Oct 31, 2024", "Annual"))
        assertEquals(emptySet(), rows.single().issues)
        assertTrue(rows.single().isImportable)
    }

    @Test
    fun `a missing name blocks the row`() {
        val rows = validate(row(1, "", "555-0192", "Nov 1, 2023", "Nov 30, 2023", "Monthly"))
        assertTrue(IntakeIssue.MISSING_NAME in rows.single().issues)
        assertTrue(!rows.single().isImportable)
    }

    @Test
    fun `a missing phone blocks the row`() {
        val rows = validate(row(1, "Alex Thompson", "", "Nov 1, 2023", "Nov 30, 2023", "Monthly"))
        assertTrue(IntakeIssue.MISSING_PHONE in rows.single().issues)
        assertTrue(!rows.single().isImportable)
    }

    @Test
    fun `a phone already in the membership blocks the row`() {
        val rows = IntakeValidator.validate(
            listOf(row(1, "Chloe Davis", "555-0889", "Nov 1, 2023", "Oct 31, 2024", "Annual")),
            existingPhones = setOf(IntakeValidator.normalisePhone("555-0889")),
        )
        assertTrue(IntakeIssue.DUPLICATE_PHONE in rows.single().issues)
        assertTrue(!rows.single().isImportable)
    }

    @Test
    fun `a phone duplicated inside the sheet flags both rows not just the second`() {
        val rows = validate(
            row(1, "Chloe Davis", "555-0889", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
            row(2, "C Davis", "5550889", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
        )
        assertTrue(rows.all { IntakeIssue.DUPLICATE_IN_BATCH in it.issues })
        assertTrue(rows.none { it.isImportable })
    }

    @Test
    fun `an end date before the start date blocks the row`() {
        val rows =
            validate(row(1, "Alex Thompson", "555-0192", "Nov 30, 2023", "Nov 1, 2023", "Monthly"))
        assertTrue(IntakeIssue.END_BEFORE_START in rows.single().issues)
        assertTrue(!rows.single().isImportable)
    }

    @Test
    fun `an unreadable date is advisory and does not block`() {
        val rows =
            validate(row(1, "Alex Thompson", "555-0192", "Nov ?, 2O23", "Nov 30, 2023", "Monthly"))
        val single = rows.single()
        assertTrue(IntakeIssue.UNREADABLE_DATE in single.issues)
        assertTrue(single.isImportable)
        assertTrue(single.needsReview)
    }

    @Test
    fun `an unrecognised plan is advisory and does not block`() {
        val rows =
            validate(row(1, "Alex Thompson", "555-0192", "Nov 1, 2023", "Nov 30, 2023", "Platnum"))
        assertTrue(IntakeIssue.UNKNOWN_PLAN in rows.single().issues)
        assertTrue(rows.single().isImportable)
    }

    @Test
    fun `Trial is accepted even though it is not a purchasable tier`() {
        val rows =
            validate(row(1, "Mike O'Brien", "555-0134", "Nov 5, 2023", "Dec 5, 2023", "Trial"))
        assertTrue(IntakeIssue.UNKNOWN_PLAN !in rows.single().issues)
        assertEquals("Trial", IntakeValidator.parsePlanLabel("trial"))
    }

    @Test
    fun `a low confidence cell asks for review without blocking`() {
        val shaky = row(1, "Alex Thompson", "555-0192", "Nov 1, 2023", "Nov 30, 2023", "Monthly")
            .let { it.copy(plan = it.plan.copy(confidence = 0.4f)) }
        val single = validate(shaky).single()
        assertTrue(IntakeIssue.LOW_CONFIDENCE in single.issues)
        assertTrue(single.isImportable)
        assertTrue(single.needsReview)
    }

    @Test
    fun `editing a cell clears its review marker regardless of the original confidence`() {
        val shaky = row(1, "Alex Thompson", "555-0192", "Nov 1, 2023", "Nov 30, 2023", "Monthly")
            .let { it.copy(plan = it.plan.copy(confidence = 0.1f)) }
        assertTrue(validate(shaky).single().needsReview)

        val fixed = shaky.copy(plan = shaky.plan.editedTo("Annual"))
        assertTrue(!validate(fixed).single().needsReview)
    }

    @Test
    fun `editing a duplicate phone clears the issue on the other row too`() {
        val before = validate(
            row(1, "Chloe Davis", "555-0889", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
            row(2, "C Davis", "555-0889", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
        )
        assertEquals(0, before.count { it.isImportable })

        // Revalidation is batch-wide, so fixing row 2 must also un-block row 1.
        val after = validate(
            row(1, "Chloe Davis", "555-0889", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
            row(2, "C Davis", "555-0890", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
        )
        assertEquals(2, after.count { it.isImportable })
    }

    @Test
    fun `the batch counts match the export's six of eight`() {
        // Eight rows: one with a duplicate against the membership, one missing a name,
        // one merely low-confidence. Six should be importable.
        val rows = IntakeValidator.validate(
            listOf(
                row(1, "Alex Thompson", "555-0192", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
                row(2, "", "555-0271", "Nov 1, 2023", "Nov 30, 2023", "Monthly"),
                row(3, "Mike O'Brien", "555-0134", "Nov 5, 2023", "Dec 5, 2023", "Trial"),
                row(4, "Chloe Davis", "555-0889", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
                row(5, "Ben Carter", "555-0653", "Nov 1, 2023", "Nov 30, 2023", "Monthly"),
                row(6, "Jessica Lee", "555-0710", "Nov 2, 2023", "Dec 2, 2023", "Trial")
                    .let { it.copy(plan = it.plan.copy(confidence = 0.3f)) },
                row(7, "David Smith", "555-0422", "Nov 1, 2023", "Oct 31, 2024", "Annual"),
                row(8, "Emily Watson", "555-0991", "Nov 1, 2023", "Nov 30, 2023", "Monthly"),
            ),
            existingPhones = setOf(IntakeValidator.normalisePhone("555-0889")),
        )
        assertEquals(6, rows.count { it.isImportable })
        assertEquals(2, rows.count { !it.isImportable })
        // The low-confidence row imports but is still marked for a look.
        assertTrue(rows.first { it.ordinal == 6 }.needsReview)
    }

    // --- phone normalisation ---------------------------------------------------------------

    @Test
    fun `phone normalisation treats country code and leading zero as the same number`() {
        val a = IntakeValidator.normalisePhone("+20 100 123 4567")
        val b = IntakeValidator.normalisePhone("0100 123 4567")
        val c = IntakeValidator.normalisePhone("01001234567")
        assertEquals(a, b)
        assertEquals(b, c)
    }

    @Test
    fun `phone normalisation of an empty or symbol only value is empty`() {
        assertEquals("", IntakeValidator.normalisePhone(""))
        assertEquals("", IntakeValidator.normalisePhone("--- / ---"))
    }

    // --- date parsing ---------------------------------------------------------------------

    @Test
    fun `dates parse in the formats the sheets use`() {
        assertEquals(LocalDate(2023, 11, 1), IntakeValidator.parseDate("Nov 1, 2023"))
        assertEquals(LocalDate(2023, 11, 1), IntakeValidator.parseDate("1 Nov 2023"))
        assertEquals(LocalDate(2023, 11, 1), IntakeValidator.parseDate("2023-11-01"))
    }

    @Test
    fun `an ambiguous numeric date is refused rather than guessed`() {
        // 01/11/2023 could be 1 Nov or 11 Jan. Guessing would put members on wrong dates.
        assertNull(IntakeValidator.parseDate("01/11/2023"))
    }

    @Test
    fun `an impossible date is refused`() {
        assertNull(IntakeValidator.parseDate("Feb 30, 2023"))
        assertNull(IntakeValidator.parseDate("Nov 1"))
        assertNull(IntakeValidator.parseDate(""))
    }

    // --- helpers --------------------------------------------------------------------------

    private fun validate(vararg rows: IntakeRow) =
        IntakeValidator.validate(rows.toList(), existingPhones = emptySet())

    private fun row(
        ordinal: Int,
        name: String,
        phone: String,
        start: String,
        end: String,
        plan: String,
    ) = IntakeRow(
        id = IntakeRowId("r$ordinal"),
        ordinal = ordinal,
        name = field(name),
        phone = field(phone),
        startDate = field(start),
        endDate = field(end),
        plan = field(plan),
        issues = emptySet(),
        bounds = null,
    )

    /** Confident by default, so a test only opts into low confidence when that is the point. */
    private fun field(value: String) = IntakeField(value, confidence = 0.99f)
}
