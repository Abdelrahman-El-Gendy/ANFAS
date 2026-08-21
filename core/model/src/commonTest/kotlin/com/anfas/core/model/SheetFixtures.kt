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
