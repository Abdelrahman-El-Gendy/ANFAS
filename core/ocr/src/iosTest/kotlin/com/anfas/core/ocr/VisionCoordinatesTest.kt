package com.anfas.core.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The coordinate flip is pure arithmetic, so it is testable on the simulator target without a
 * camera. This is the highest-value iOS test available.
 */
class VisionCoordinatesTest {

    @Test
    fun `a box near the top of the page maps to a small top value`() {
        // Vision: origin near the TOP means a high y (measured from the bottom).
        val bounds = visionBoxToOcrBounds(originX = 0.1, originY = 0.9, width = 0.2, height = 0.05)
        assertEquals(0.1f, bounds.left)
        assertEquals(0.05f, bounds.top, absoluteTolerance = 1e-5f)
        assertEquals(0.1f, bounds.bottom, absoluteTolerance = 1e-5f)
    }

    @Test
    fun `a box near the bottom of the page maps to a large top value`() {
        val bounds = visionBoxToOcrBounds(originX = 0.1, originY = 0.02, width = 0.2, height = 0.05)
        assertTrue(bounds.top > 0.9f, "top=${bounds.top}")
    }

    @Test
    fun `reading order is preserved after the flip`() {
        // Two lines, the first higher up the page. After conversion the first must sort first by
        // top -- if the flip is wrong, row order silently reverses.
        val upper = visionBoxToOcrBounds(0.1, 0.90, 0.2, 0.04)
        val lower = visionBoxToOcrBounds(0.1, 0.70, 0.2, 0.04)
        assertTrue(upper.top < lower.top, "upper=${upper.top} lower=${lower.top}")
    }

    @Test
    fun `values a hair outside the unit square are clamped rather than throwing`() {
        // Vision genuinely reports these; OcrBounds' own constructor would throw.
        val bounds = visionBoxToOcrBounds(-0.0001, -0.0002, 1.0003, 1.0004)
        assertEquals(0f, bounds.left)
        assertEquals(1f, bounds.right)
    }

    @Test
    fun `height is preserved through the flip`() {
        val bounds = visionBoxToOcrBounds(0.1, 0.5, 0.2, 0.08)
        assertEquals(0.08f, bounds.height, absoluteTolerance = 1e-5f)
    }
}
