package com.anfas.core.model

/**
 * Turns recognised text lines into [IntakeRow]s.
 *
 * This is the hard part of OCR intake, and the reason it lives here rather than beside the
 * platform code: ML Kit and Vision both return **lines with boxes, not a table**. Reconstructing
 * rows and columns is pure geometry plus content heuristics, and `:core:model` may depend on
 * nothing — which makes it impossible to accidentally reach for a platform API and guarantees the
 * algorithm stays testable from hand-written fixtures, with no camera.
 *
 * Deliberately knows nothing about validation. [IntakeValidator] runs afterwards.
 */
object IntakeSheetParser {

    /**
     * Every magic number in one injectable place, so tuning against real photographs is a
     * parameter change rather than an edit scattered through the algorithm.
     */
    data class Tuning(
        /** Two lines are on the same row if they overlap vertically by at least this fraction. */
        val rowOverlapRatio: Float = 0.5f,
        /** Stricter retry when a component chains into a blob because the page is skewed. */
        val strictRowOverlapRatio: Float = 0.75f,
        val maxLinesPerRow: Int = 12,
        /** Rows to search for a header before giving up on one. */
        val headerSearchRows: Int = 3,
        /** Minimum x-gap that separates two columns, as a fraction of page width. */
        val minColumnGap: Float = 0.03f,
        /** A single cell wider than this is a footer or signature line, not a member. */
        val footerCellWidth: Float = 0.6f,
    )

    /**
     * [direction] is detected from the **paper**, not from the app's language: an Arabic-speaking
     * receptionist may well be holding an English-printed form.
     */
    data class ParsedSheet(
        val rows: List<IntakeRow>,
        val direction: SheetDirection,
        val headerFound: Boolean,
        val discarded: Int,
    )

    enum class SheetDirection { LeftToRight, RightToLeft }

    private enum class Column { ORDINAL, NAME, PHONE, START, END, PLAN }

    fun parse(
        lines: List<OcrLine>,
        tuning: Tuning = Tuning(),
        newRowId: (Int) -> IntakeRowId,
    ): ParsedSheet {
        // Stage 0 — clean. Bounds arrive already clamped, because platform recognisers build
        // them through OcrBounds.normalised; by the time a value reaches here it is valid.
        val cleaned = lines
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() && it.bounds.width > 0f && it.bounds.height > 0f }
        if (cleaned.isEmpty()) {
            return ParsedSheet(emptyList(), SheetDirection.LeftToRight, false, lines.size)
        }

        // Stage 2 — group lines into rows by vertical overlap.
        val rawRows = clusterRows(cleaned, tuning)

        // Stage 3 — find a header, which is the strongest signal for both columns and direction.
        val headerIndex = rawRows.indexOfFirst { headerScore(it) >= HEADER_MIN_MATCHES }
            .takeIf { it in 0 until minOf(tuning.headerSearchRows, rawRows.size) }
        val headerRow = headerIndex?.let { rawRows[it] }
        val direction = headerRow?.let(::detectDirection) ?: SheetDirection.LeftToRight

        val dataRows = rawRows.filterIndexed { index, _ -> index != headerIndex }

        var discarded = 0
        val rows = mutableListOf<IntakeRow>()
        dataRows.forEach { row ->
            // Stage 6 — drop non-member lines: a lone wide cell is a "Total:" or signature line.
            if (row.size == 1 && row.single().bounds.width > tuning.footerCellWidth) {
                discarded++
                return@forEach
            }
            val assigned = assign(row, direction)
            val built = buildRow(assigned, ordinalFallback = rows.size + 1, newRowId = newRowId)
            if (built == null) discarded++ else rows.add(built)
        }

        return ParsedSheet(
            rows = rows,
            direction = direction,
            headerFound = headerRow != null,
            discarded = discarded,
        )
    }

    /**
     * Groups by **vertical overlap**, not centre distance.
     *
     * Overlap survives a page tilt where a centre-distance test would split the row: at normal
     * sheet widths a 2-degree skew shifts a row's ends by well under half a line height. Uses
     * union-find over lines sorted by top edge, which is O(n log n) and — importantly —
     * deterministic, so ML Kit's and Vision's different traversal orders are interchangeable.
     */
    private fun clusterRows(lines: List<OcrLine>, tuning: Tuning): List<List<OcrLine>> {
        fun group(items: List<OcrLine>, ratio: Float): List<List<OcrLine>> {
            val sorted = items.sortedBy { it.bounds.top }
            val parent = IntArray(sorted.size) { it }
            fun find(a: Int): Int {
                var x = a
                while (parent[x] != x) {
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                }
                return x
            }
            fun union(a: Int, b: Int) {
                val ra = find(a)
                val rb = find(b)
                if (ra != rb) parent[rb] = ra
            }
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    // Sorted by top, so once a candidate starts below this one's bottom, nothing
                    // further can overlap it either.
                    if (sorted[j].bounds.top > sorted[i].bounds.bottom) break
                    if (overlapRatio(sorted[i].bounds, sorted[j].bounds) >= ratio) union(i, j)
                }
            }
            return sorted.indices.groupBy { find(it) }.values.map { idx -> idx.map { sorted[it] } }
        }

        val initial = group(lines, tuning.rowOverlapRatio)
        // Escape hatch: a badly skewed page chains components into a blob. Retry that component
        // once, stricter, then give up on it rather than emitting nonsense.
        return initial.flatMap { component ->
            if (component.size <= tuning.maxLinesPerRow) {
                listOf(component)
            } else {
                group(component, tuning.strictRowOverlapRatio)
            }
        }.sortedBy { row -> row.minOf { it.bounds.top } }
    }

    private fun overlapRatio(a: OcrBounds, b: OcrBounds): Float {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (overlap <= 0f) return 0f
        return overlap / minOf(a.height, b.height)
    }

    /** How many of the five expected headers this row matches, in either language. */
    private fun headerScore(row: List<OcrLine>): Int =
        row.count { line -> HEADER_VOCABULARY.any { it == line.text.normaliseForMatching() } }

    /**
     * Reading direction from the paper: if NAME sits to the right of PLAN in x, the sheet is
     * Arabic-printed.
     */
    private fun detectDirection(header: List<OcrLine>): SheetDirection {
        val name = header.firstOrNull { it.text.normaliseForMatching() in NAME_HEADERS }
        val plan = header.firstOrNull { it.text.normaliseForMatching() in PLAN_HEADERS }
        if (name == null || plan == null) return SheetDirection.LeftToRight
        return if (name.bounds.left > plan.bounds.left) {
            SheetDirection.RightToLeft
        } else {
            SheetDirection.LeftToRight
        }
    }

    /**
     * Assigns a row's cells to columns by **content**, not position.
     *
     * Content-first is what saves the feature on real sheets: photographs get cropped, columns
     * shift, and a header is often missing entirely — but a cell of mostly digits is a phone
     * number wherever it sits, and a parseable date is a date.
     */
    private fun assign(row: List<OcrLine>, direction: SheetDirection): Map<Column, List<OcrLine>> {
        val ordered = if (direction == SheetDirection.RightToLeft) {
            row.sortedByDescending { it.bounds.left }
        } else {
            row.sortedBy { it.bounds.left }
        }

        val result = mutableMapOf<Column, MutableList<OcrLine>>()
        val dates = mutableListOf<OcrLine>()
        val unclaimed = mutableListOf<OcrLine>()

        ordered.forEachIndexed { index, line ->
            when {
                looksLikePhone(line.text) -> result.getOrPut(Column.PHONE) { mutableListOf() }
                    .add(line)

                IntakeValidator.parseDate(line.text) != null -> dates.add(line)

                IntakeValidator.parsePlanLabel(line.text) != null ->
                    result.getOrPut(Column.PLAN) { mutableListOf() }.add(line)

                looksLikeOrdinal(line.text, line.bounds, index) ->
                    result.getOrPut(Column.ORDINAL) { mutableListOf() }.add(line)

                else -> unclaimed.add(line)
            }
        }

        // Two dates: the EARLIER is the start, regardless of x. On an Arabic sheet the leftmost
        // date is the end column, so ordering by position would swap them.
        dates.sortedBy { IntakeValidator.parseDate(it.text) }.forEachIndexed { i, line ->
            val column = if (i == 0) Column.START else Column.END
            result.getOrPut(column) { mutableListOf() }.add(line)
        }

        // A phone that wrapped mid-number leaves a short digit-only fragment ("4567") with too
        // few digits to classify on its own. Attach it to the phone cell when it sits
        // horizontally adjacent, or the number is silently truncated -- which is worse than an
        // obviously-wrong number, because staff would not notice.
        val phoneCells = result[Column.PHONE]
        if (phoneCells != null) {
            val continuations = unclaimed.filter { candidate ->
                isDigitsOnly(candidate.text) &&
                    phoneCells.any { horizontallyAdjacent(it.bounds, candidate.bounds) }
            }
            continuations.forEach { phoneCells.add(it) }
            unclaimed.removeAll(continuations)
            // Keep reading order stable so the fragments join in the right sequence.
            phoneCells.sortBy {
                if (direction ==
                    SheetDirection.RightToLeft
                ) {
                    -it.bounds.left
                } else {
                    it.bounds.left
                }
            }
        }

        // Whatever is left and looks like words is the name. A phone-classified cell can never
        // become the name while an unclaimed candidate exists.
        unclaimed.filter { hasLetters(it.text) }
            .forEach { result.getOrPut(Column.NAME) { mutableListOf() }.add(it) }

        return result
    }

    /**
     * Builds the row, merging multi-line cells.
     *
     * Several lines legitimately land in one cell: a wrapped name, or a phone split as
     * "0100 123" + "4567". Phone parts join with no separator, everything else with a space.
     * Merged confidence is the **minimum** of the parts — pessimistic on purpose, matching the
     * deliberately cautious [IntakeField.REVIEW_THRESHOLD].
     */
    private fun buildRow(
        cells: Map<Column, List<OcrLine>>,
        ordinalFallback: Int,
        newRowId: (Int) -> IntakeRowId,
    ): IntakeRow? {
        fun field(column: Column, separator: String = " "): IntakeField {
            val parts = cells[column].orEmpty()
            if (parts.isEmpty()) {
                // Confidence 0, not 1, so the cell gets its amber review marker rather than
                // looking like something the engine was sure about.
                return IntakeField(value = "", confidence = 0f)
            }
            return IntakeField(
                value = parts.joinToString(separator) { it.text },
                confidence = parts.minOf { it.confidence },
            )
        }

        val name = field(Column.NAME)
        val phone = field(Column.PHONE, separator = "")
        if (name.isBlank && phone.isBlank) return null

        val ordinal = cells[Column.ORDINAL]?.firstOrNull()?.text
            ?.foldDigitsToAscii()?.toIntOrNull() ?: ordinalFallback

        val allBounds = cells.values.flatten().map { it.bounds }
        return IntakeRow(
            id = newRowId(ordinal),
            ordinal = ordinal,
            name = name,
            phone = phone,
            startDate = field(Column.START),
            endDate = field(Column.END),
            plan = field(Column.PLAN),
            // Validation is not this parser's job.
            issues = emptySet(),
            // Union of the row's boxes, so the overlay covers the whole handwritten line, which
            // is what staff cross-reference against the table.
            bounds = allBounds.union(),
        )
    }

    private fun looksLikePhone(text: String): Boolean {
        val digits = text.foldDigitsToAscii().filter { it.isDigit() }
        return digits.length >= MIN_PHONE_DIGITS
    }

    private fun looksLikeOrdinal(text: String, bounds: OcrBounds, index: Int): Boolean {
        val digits = text.foldDigitsToAscii()
        if (digits.length > 3 || !digits.all { it.isDigit() }) return false
        return bounds.width < ORDINAL_MAX_WIDTH || index == 0
    }

    private fun hasLetters(text: String): Boolean = text.count { it.isLetter() } >= 2

    private fun isDigitsOnly(text: String): Boolean {
        val folded = text.foldDigitsToAscii().filter { !it.isWhitespace() }
        return folded.isNotEmpty() && folded.all { it.isDigit() || it in PHONE_PUNCTUATION }
    }

    /** Adjacent, or overlapping, within a tolerance of a few percent of page width. */
    private fun horizontallyAdjacent(a: OcrBounds, b: OcrBounds): Boolean {
        val gap = maxOf(a.left, b.left) - minOf(a.right, b.right)
        return gap <= ADJACENCY_TOLERANCE
    }

    private fun List<OcrBounds>.union(): OcrBounds? {
        if (isEmpty()) return null
        return OcrBounds(
            left = minOf { it.left },
            top = minOf { it.top },
            right = maxOf { it.right },
            bottom = maxOf { it.bottom },
        )
    }

    private const val MIN_PHONE_DIGITS = 7
    private const val ORDINAL_MAX_WIDTH = 0.05f
    private const val HEADER_MIN_MATCHES = 3
    private const val ADJACENCY_TOLERANCE = 0.04f
    private val PHONE_PUNCTUATION = setOf('-', '+', '(', ')')

    private val NAME_HEADERS = setOf("name", "الاسم")
    private val PLAN_HEADERS = setOf("plan", "الخطة", "الباقة")

    /** Bilingual, because a sheet's own language is not the app's. */
    private val HEADER_VOCABULARY = setOf(
        "#", "no", "name", "phone", "mobile", "start", "end", "plan",
        "الاسم", "الهاتف", "موبايل", "بداية", "من", "نهاية", "إلى", "الخطة", "الباقة",
    )
}
