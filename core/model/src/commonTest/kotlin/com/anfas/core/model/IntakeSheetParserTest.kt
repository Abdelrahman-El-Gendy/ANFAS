package com.anfas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntakeSheetParserTest {

    private fun parse(lines: List<OcrLine>) =
        IntakeSheetParser.parse(lines, newRowId = { IntakeRowId("r$it") })

    @Test
    fun `a clean sheet with a header yields one row per line`() {
        val lines = englishHeader() +
            sheetRow(
                0.12f,
                ordinal = "1",
                name = "Alex Thompson",
                phone = "555-0192",
                start = "Nov 1, 2023",
                end = "Oct 31, 2024",
                plan = "Annual",
            ) +
            sheetRow(
                0.18f,
                ordinal = "2",
                name = "Ben Carter",
                phone = "555-0653",
                start = "Nov 1, 2023",
                end = "Nov 30, 2023",
                plan = "Monthly",
            )

        val sheet = parse(lines)

        assertTrue(sheet.headerFound)
        assertEquals(2, sheet.rows.size)
        assertEquals(listOf("Alex Thompson", "Ben Carter"), sheet.rows.map { it.name.value })
        // Raw OCR text is preserved deliberately -- IntakeField keeps what the engine read so a
        // misread like "555-O192" is visible to staff. Normalisation happens in the validator.
        assertEquals(listOf("555-0192", "555-0653"), sheet.rows.map { it.phone.value })
        assertEquals(listOf("Annual", "Monthly"), sheet.rows.map { it.plan.value })
    }

    @Test
    fun `the printed ordinal is preserved rather than renumbered`() {
        val lines = englishHeader() +
            sheetRow(0.12f, ordinal = "7", name = "Alex Thompson", phone = "555-0192")

        assertEquals(7, parse(lines).rows.single().ordinal)
    }

    @Test
    fun `a cropped photo with no header still parses by content`() {
        // No header at all -- this is the case that decides whether the feature survives on real
        // photographs, where the top of the sheet is routinely cut off.
        val lines = sheetRow(
            0.12f,
            name = "Alex Thompson",
            phone = "555-0192",
            start = "Nov 1, 2023",
            end = "Oct 31, 2024",
            plan = "Annual",
        )

        val sheet = parse(lines)

        assertTrue(!sheet.headerFound)
        val row = sheet.rows.single()
        assertEquals("Alex Thompson", row.name.value)
        assertEquals("555-0192", row.phone.value)
        assertEquals("Annual", row.plan.value)
    }

    @Test
    fun `an Arabic-printed sheet produces the same logical rows`() {
        val lines = arabicHeader() +
            sheetRow(
                0.12f,
                ordinal = "1",
                name = "عمر خالد",
                phone = "555-0192",
                start = "1 نوفمبر 2023",
                end = "30 نوفمبر 2023",
                plan = "شهري",
                mirrored = true,
            )

        val sheet = parse(lines)

        assertEquals(IntakeSheetParser.SheetDirection.RightToLeft, sheet.direction)
        val row = sheet.rows.single()
        assertEquals("عمر خالد", row.name.value)
        assertEquals("555-0192", row.phone.value)
        assertEquals("شهري", row.plan.value)
    }

    @Test
    fun `two dates are ordered by value not by position`() {
        // On a mirrored sheet the leftmost date is the END column, so ordering by x would swap
        // start and end -- putting members on inverted terms.
        val lines = arabicHeader() +
            sheetRow(
                0.12f,
                name = "عمر خالد",
                phone = "555-0192",
                start = "1 نوفمبر 2023",
                end = "30 نوفمبر 2023",
                mirrored = true,
            )

        val row = parse(lines).rows.single()
        assertEquals("1 نوفمبر 2023", row.startDate.value)
        assertEquals("30 نوفمبر 2023", row.endDate.value)
    }

    @Test
    fun `a wrapped name across two lines is merged with a space`() {
        val lines = listOf(
            ocrLine("Alexander", 0.10f, 0.12f, 0.24f, 0.15f),
            ocrLine("Thompson-Smith", 0.10f, 0.125f, 0.30f, 0.155f),
            ocrLine("555-0192", 0.35f, 0.12f, 0.52f, 0.15f),
        )

        assertEquals("Alexander Thompson-Smith", parse(lines).rows.single().name.value)
    }

    @Test
    fun `a phone split across two lines is joined without a separator`() {
        val lines = listOf(
            ocrLine("Alex Thompson", 0.10f, 0.12f, 0.30f, 0.15f),
            ocrLine("0100 123", 0.35f, 0.12f, 0.45f, 0.15f),
            ocrLine("4567", 0.46f, 0.125f, 0.52f, 0.155f),
        )

        // "4567" alone has too few digits to classify as a phone, so without the adjacency rule
        // it would be dropped and the number silently truncated -- worse than an obviously wrong
        // number, because nobody would notice.
        val phone = parse(lines).rows.single().phone.value
        assertEquals("01001234567", IntakeValidator.normalisePhone(phone))
    }

    @Test
    fun `a merged cell takes the lowest confidence of its parts`() {
        val lines = listOf(
            ocrLine("Alexander", 0.10f, 0.12f, 0.24f, 0.15f, confidence = 0.99f),
            ocrLine("Thompson", 0.10f, 0.125f, 0.30f, 0.155f, confidence = 0.40f),
            ocrLine("555-0192", 0.35f, 0.12f, 0.52f, 0.15f),
        )

        // Pessimistic on purpose: the cell should ask for review, not look confident.
        assertEquals(0.40f, parse(lines).rows.single().name.confidence)
    }

    @Test
    fun `a missing plan cell yields a blank field flagged for review`() {
        val lines = sheetRow(
            0.12f,
            name = "Alex Thompson",
            phone = "555-0192",
            start = "Nov 1, 2023",
            end = "Oct 31, 2024",
        )

        val row = parse(lines).rows.single()
        assertTrue(row.plan.isBlank)
        // Confidence 0, not 1: an absent cell must not look like something the engine was sure of.
        assertEquals(0f, row.plan.confidence)
        assertTrue(row.plan.needsReview)
    }

    @Test
    fun `a footer or signature line is discarded rather than becoming a member`() {
        val lines = englishHeader() +
            sheetRow(0.12f, name = "Alex Thompson", phone = "555-0192") +
            listOf(ocrLine("Total: 8 members signed up today", 0.05f, 0.30f, 0.95f, 0.34f))

        val sheet = parse(lines)
        assertEquals(1, sheet.rows.size)
        assertEquals(1, sheet.discarded)
    }

    @Test
    fun `input order never matters`() {
        // ML Kit and Vision traverse in different orders; the parser must be indifferent.
        val lines = englishHeader() +
            sheetRow(0.12f, ordinal = "1", name = "Alex Thompson", phone = "555-0192") +
            sheetRow(0.18f, ordinal = "2", name = "Ben Carter", phone = "555-0653")

        val forward = parse(lines).rows.map { it.name.value to it.phone.value }
        val reversed = parse(lines.reversed()).rows.map { it.name.value to it.phone.value }
        assertEquals(forward, reversed)
    }

    @Test
    fun `a mild page skew does not split rows`() {
        // Each cell nudged progressively down, as a 2-degree tilt would do. Overlap-based
        // clustering must still see one row; a centre-distance test would break here.
        val lines = listOf(
            ocrLine("Alex Thompson", 0.10f, 0.120f, 0.30f, 0.150f),
            ocrLine("555-0192", 0.35f, 0.124f, 0.52f, 0.154f),
            ocrLine("Nov 1, 2023", 0.55f, 0.128f, 0.68f, 0.158f),
            ocrLine("Annual", 0.86f, 0.132f, 0.97f, 0.162f),
        )

        val sheet = parse(lines)
        assertEquals(1, sheet.rows.size, "skew split the row into ${sheet.rows.size}")
        assertEquals("Alex Thompson", sheet.rows.single().name.value)
    }

    @Test
    fun `a photograph of nothing produces no rows and does not throw`() {
        val lines = listOf(
            ocrLine("", 0.1f, 0.1f, 0.2f, 0.12f),
            ocrLine("...", 0.4f, 0.5f, 0.42f, 0.52f),
        )
        assertEquals(0, parse(lines).rows.size)
        assertEquals(0, parse(emptyList()).rows.size)
    }

    @Test
    fun `bounds a hair outside 0 to 1 are clamped rather than throwing`() {
        // Vision genuinely reports these. The constructor would throw; normalised() must not.
        val line = OcrLine(
            text = "Alex Thompson",
            confidence = 0.9f,
            bounds = OcrBounds.normalised(-0.0001f, 0.12f, 1.0000003f, 0.15f),
        )
        val phone = ocrLine("555-0192", 0.35f, 0.12f, 0.52f, 0.15f)
        assertEquals(1, parse(listOf(line, phone)).rows.size)
    }

    @Test
    fun `the row bounds cover every cell so the overlay spans the whole line`() {
        val lines = sheetRow(0.12f, name = "Alex Thompson", phone = "555-0192", plan = "Annual")

        val bounds = requireNotNull(parse(lines).rows.single().bounds)
        assertTrue(bounds.left <= Col.name.first + 0.001f, "left=${bounds.left}")
        assertTrue(bounds.right >= Col.plan.second - 0.001f, "right=${bounds.right}")
    }

    @Test
    fun `a row with neither name nor phone is dropped`() {
        val lines = englishHeader() +
            sheetRow(0.12f, start = "Nov 1, 2023", end = "Oct 31, 2024", plan = "Annual")

        val sheet = parse(lines)
        assertEquals(0, sheet.rows.size)
        assertEquals(1, sheet.discarded)
    }

    @Test
    fun `parsed rows feed straight into the validator`() {
        val lines = englishHeader() +
            sheetRow(
                0.12f,
                ordinal = "1",
                name = "Alex Thompson",
                phone = "555-0192",
                start = "Nov 1, 2023",
                end = "Oct 31, 2024",
                plan = "Annual",
            ) +
            sheetRow(
                0.18f,
                ordinal = "2",
                name = "",
                phone = "555-0653",
                start = "Nov 1, 2023",
                end = "Nov 30, 2023",
                plan = "Monthly",
            )

        val parsed = parse(lines).rows
        val validated = IntakeValidator.validate(parsed, existingPhones = emptySet())

        // Row 2 has no name, so it is blocked -- the parser and validator compose without glue.
        assertEquals(1, validated.count { it.isImportable })
    }

    /**
     * Regression: ML Kit reports words, not cells. Before [IntakeSheetParser] grouped boxes into
     * cells first, "Nov" / "1," / "2023" each failed every content test, so all three dates and
     * the plan came out blank -- and the leftovers were appended to the member's *name*. The
     * screen still showed "4 of 4 rows ready for import", which is the dangerous part.
     */
    @Test
    fun `word-level boxes parse the same as whole-cell boxes`() {
        val words = englishHeader() + wordLevelRow(
            top = 0.20f,
            ordinal = "1",
            name = "Omar Hassan",
            phone = "01001234567",
            start = "Nov 1, 2023",
            end = "Dec 1, 2023",
            plan = "Monthly",
        )

        val parsed = IntakeSheetParser.parse(words, newRowId = { IntakeRowId("r$it") })

        val row = parsed.rows.single()
        assertEquals("Omar Hassan", row.name.value)
        assertEquals("01001234567", row.phone.value)
        assertEquals("Nov 1, 2023", row.startDate.value)
        assertEquals("Dec 1, 2023", row.endDate.value)
        assertEquals("Monthly", row.plan.value)
    }

    /**
     * A fused token like "2023Monthly" — which ML Kit really does produce when two columns are
     * printed close together — must not end up in the name. A blank cell with a review marker is
     * recoverable; a member imported as "Omar Hassan 2023Monthly" is not.
     */
    @Test
    fun `unparseable numeric debris never lands in the name`() {
        val row = englishHeader() + listOf(
            ocrLine("1", 0.04f, 0.20f, 0.08f, 0.23f),
            ocrLine("Omar Hassan", 0.10f, 0.20f, 0.32f, 0.23f),
            ocrLine("01001234567", 0.35f, 0.20f, 0.52f, 0.23f),
            ocrLine("2023Monthly", 0.86f, 0.20f, 0.97f, 0.23f),
        )

        val parsed = IntakeSheetParser.parse(row, newRowId = { IntakeRowId("r$it") })

        assertEquals("Omar Hassan", parsed.rows.single().name.value)
    }
}
