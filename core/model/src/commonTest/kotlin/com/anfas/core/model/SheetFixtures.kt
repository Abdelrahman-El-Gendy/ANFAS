package com.anfas.core.model

/**
 * Hand-written OCR output, so the parser is testable with **no camera and no device**. That is the
 * entire reason the algorithm lives in `:core:model`.
 *
 * Kotlin source rather than JSON resources on purpose: commonTest resource loading across
 * iosArm64 / iosSimulatorArm64 / jvm / androidHostTest is a needless fight, and `:core:model` must
 * not gain a resources story.
 */
internal fun ocrLine(
    text: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    confidence: Float = 0.95f,
) = OcrLine(
    text = text,
    confidence = confidence,
    bounds = OcrBounds.normalised(left, top, right, bottom),
)

/** Column x-extents for a typical English sheet: # | Name | Phone | Start | End | Plan. */
internal object Col {
    val ordinal = 0.04f to 0.08f
    val name = 0.10f to 0.32f
    val phone = 0.35f to 0.52f
    val start = 0.55f to 0.68f
    val end = 0.70f to 0.83f
    val plan = 0.86f to 0.97f
}

/** One row of cells at a given vertical band. */
internal fun sheetRow(
    top: Float,
    height: Float = 0.03f,
    ordinal: String? = null,
    name: String? = null,
    phone: String? = null,
    start: String? = null,
    end: String? = null,
    plan: String? = null,
    confidence: Float = 0.95f,
    mirrored: Boolean = false,
): List<OcrLine> {
    fun cell(text: String?, x: Pair<Float, Float>): OcrLine? {
        if (text == null) return null
        val (l, r) = if (mirrored) (1f - x.second) to (1f - x.first) else x
        return ocrLine(text, l, top, r, top + height, confidence)
    }
    return listOfNotNull(
        cell(ordinal, Col.ordinal),
        cell(name, Col.name),
        cell(phone, Col.phone),
        cell(start, Col.start),
        cell(end, Col.end),
        cell(plan, Col.plan),
    )
}

internal fun englishHeader(top: Float = 0.05f, mirrored: Boolean = false) = sheetRow(
    top = top,
    ordinal = "#",
    name = "Name",
    phone = "Phone",
    start = "Start",
    end = "End",
    plan = "Plan",
    mirrored = mirrored,
)

internal fun arabicHeader(top: Float = 0.05f) = sheetRow(
    top = top,
    ordinal = "#",
    name = "الاسم",
    phone = "الهاتف",
    start = "بداية",
    end = "نهاية",
    plan = "الخطة",
    mirrored = true,
)

/**
 * The same row, but split into word-level boxes the way ML Kit actually reports it.
 *
 * Worth a fixture of its own: ML Kit returns `Text.Element`s (words), Vision returns whole
 * lines, and the parser has to give the same answer either way. Word gaps here are 0.006 of page
 * width, well inside [IntakeSheetParser.Tuning.minColumnGap], matching what a real printed sheet
 * measured.
 */
internal fun wordLevelRow(
    top: Float,
    height: Float = 0.03f,
    ordinal: String? = null,
    name: String? = null,
    phone: String? = null,
    start: String? = null,
    end: String? = null,
    plan: String? = null,
    confidence: Float = 0.95f,
): List<OcrLine> {
    fun words(text: String?, x: Pair<Float, Float>): List<OcrLine> {
        if (text == null) return emptyList()
        val parts = text.split(' ').filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()
        // Lay the words out across the column with a 0.006 gap, proportional to their lengths so
        // a long word gets a wide box -- the parser reads edges, so this has to be plausible.
        val gap = 0.006f
        val totalChars = parts.sumOf { it.length }
        val usable = (x.second - x.first) - gap * (parts.size - 1)
        var cursor = x.first
        return parts.map { word ->
            val width = usable * (word.length.toFloat() / totalChars)
            val line = ocrLine(word, cursor, top, cursor + width, top + height, confidence)
            cursor += width + gap
            line
        }
    }
    return words(ordinal, Col.ordinal) + words(name, Col.name) + words(phone, Col.phone) +
        words(start, Col.start) + words(end, Col.end) + words(plan, Col.plan)
}
