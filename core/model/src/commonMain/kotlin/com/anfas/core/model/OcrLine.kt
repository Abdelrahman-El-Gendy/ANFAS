package com.anfas.core.model

/**
 * One line of recognised text with where it sits on the page.
 *
 * [bounds] is normalised 0..1 with a **top-left origin**, so every platform converts into this
 * frame and the parser never sees a platform coordinate system. iOS Vision reports a bottom-left
 * origin and must flip; getting that wrong silently reverses row order.
 */
data class OcrLine(val text: String, val confidence: Float, val bounds: OcrBounds)
