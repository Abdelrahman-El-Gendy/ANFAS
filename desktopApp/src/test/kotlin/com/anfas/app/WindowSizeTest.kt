package com.anfas.app

import com.anfas.core.designsystem.AnfasBreakpoints
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The desktop window must open — and must stay — above the layout breakpoint.
 *
 * This is a reachability rule, not a cosmetic one, which is why it is worth a test on a launcher
 * that otherwise holds no logic. Compose's default window is 800x600, below
 * `AnfasBreakpoints.tabletMax`, so the desktop app used to open on the *phone* layout. That layout
 * shows the bottom bar, and the bar carries only the four `Placement.Primary` destinations — so
 * Announcements and Equipment, both `Placement.DesktopOnly` and rail-only by design, had **no entry
 * point at all** in the desktop app unless the user happened to drag the window wider.
 *
 * Asserted against `AnfasBreakpoints.tabletMax` rather than a copied `1024`, so moving the
 * breakpoint fails here instead of silently un-reaching two features again.
 */
class WindowSizeTest {

    @Test
    fun `the window opens wide enough for the rail layout`() {
        assertTrue(
            INITIAL_WINDOW_WIDTH > AnfasBreakpoints.tabletMax,
            "opens at $INITIAL_WINDOW_WIDTH, at or below the ${AnfasBreakpoints.tabletMax} " +
                "breakpoint — the desktop app would start on the phone layout and " +
                "Announcements/Equipment would be unreachable",
        )
    }

    /** A feature that disappears when a window is dragged narrower is a bug, not responsiveness. */
    @Test
    fun `the window cannot be resized below the rail layout`() {
        assertTrue(
            MIN_WINDOW_WIDTH_PX > AnfasBreakpoints.tabletMax.value,
            "the ${MIN_WINDOW_WIDTH_PX}px resize floor is at or below the " +
                "${AnfasBreakpoints.tabletMax} breakpoint, so the rail can still be resized away",
        )
    }

    /**
     * Past the breakpoint rather than exactly on it. Window chrome and dp/px rounding both eat a
     * few units, and landing back on the phone layout by one pixel is the same bug again.
     */
    @Test
    fun `the resize floor clears the breakpoint with room to spare`() {
        val margin = MIN_WINDOW_WIDTH_PX - AnfasBreakpoints.tabletMax.value
        assertTrue(
            margin >= MIN_BREAKPOINT_MARGIN,
            "only ${margin}dp of margin over the breakpoint; want at least $MIN_BREAKPOINT_MARGIN",
        )
    }

    @Test
    fun `the window opens no smaller than it can be resized`() {
        assertTrue(
            INITIAL_WINDOW_WIDTH.value >= MIN_WINDOW_WIDTH_PX,
            "opens at $INITIAL_WINDOW_WIDTH but the floor is ${MIN_WINDOW_WIDTH_PX}px",
        )
        assertTrue(
            INITIAL_WINDOW_HEIGHT.value >= MIN_WINDOW_HEIGHT_PX,
            "opens at $INITIAL_WINDOW_HEIGHT but the floor is ${MIN_WINDOW_HEIGHT_PX}px",
        )
    }

    private companion object {
        const val MIN_BREAKPOINT_MARGIN = 16f
    }
}
