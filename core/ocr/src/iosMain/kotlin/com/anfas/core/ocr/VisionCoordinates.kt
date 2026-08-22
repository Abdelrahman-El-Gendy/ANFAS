package com.anfas.core.ocr

import com.anfas.core.model.OcrBounds

/**
 * Vision reports normalised boxes with a **bottom-left origin**; the rest of the app uses
 * top-left. Getting this wrong flips every overlay box vertically *and* reverses row order, and
 * it does so silently — the app still works, it just points at the wrong lines.
 *
 * Isolated in a pure function with its own test, because it is the one piece of the iOS path that
 * can be verified without a device.
 */
internal fun visionBoxToOcrBounds(
    originX: Double,
    originY: Double,
    width: Double,
    height: Double,
): OcrBounds = OcrBounds.normalised(
    left = originX.toFloat(),
    // Vision's origin is the BOTTOM-left corner, so the top edge is measured from the far side.
    top = (1.0 - (originY + height)).toFloat(),
    right = (originX + width).toFloat(),
    bottom = (1.0 - originY).toFloat(),
)
