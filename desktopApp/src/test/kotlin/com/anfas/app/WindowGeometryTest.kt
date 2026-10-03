package com.anfas.app

import java.awt.Rectangle
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Restoring a window is the rare feature whose bug is unrecoverable from inside the app: a window
 * placed on coordinates no screen covers cannot be found and dragged back, so the app appears to
 * launch and do nothing. Every case below is a real arrangement someone will have — an external
 * monitor unplugged, a laptop docked with the second screen to the *left*, a saved size from
 * before the resize floor existed.
 *
 * Pure `Rectangle` maths, so none of it needs a display and it all runs in `./gradlew check`.
 */
class WindowGeometryTest {

    private val primary = Rectangle(0, 0, 1920, 1080)

    @Test
    fun `nothing saved means nothing to restore`() {
        assertNull(resolveWindowGeometry(saved = null, screens = listOf(primary)))
    }

    @Test
    fun `no screens means nothing to restore`() {
        // Headless, which is also what a CI machine reports.
        assertNull(resolveWindowGeometry(saved = geometry(x = 100, y = 100), screens = emptyList()))
    }

    @Test
    fun `a window fully on screen is restored exactly`() {
        val saved = geometry(x = 300, y = 200, width = 1280, height = 840)

        assertEquals(saved, resolveWindowGeometry(saved, listOf(primary)))
    }

    /**
     * The case that makes a naive "clamp to the primary screen" implementation wrong. A monitor
     * arranged to the left of the primary one has negative x, and rejecting negative coordinates
     * would refuse to restore a window for everyone with that setup.
     */
    @Test
    fun `a window on a monitor to the left keeps its negative coordinates`() {
        val leftMonitor = Rectangle(-1920, 0, 1920, 1080)
        val saved = geometry(x = -1500, y = 120, width = 1280, height = 840)

        assertEquals(saved, resolveWindowGeometry(saved, listOf(leftMonitor, primary)))
    }

    /** The unplugged-monitor case: the whole point of the feature. */
    @Test
    fun `a window saved on a screen that no longer exists is not restored`() {
        val saved = geometry(x = 2400, y = 300, width = 1280, height = 840)

        assertNull(
            resolveWindowGeometry(saved, listOf(primary)),
            "restored a window onto coordinates no screen covers — unrecoverable from in-app",
        )
    }

    /**
     * Nudged back into reach, not recentred. Parking a window mostly off an edge is something
     * people do on purpose, so the saved position is respected as far as it can be — only the last
     * [MIN_VISIBLE_WIDTH] is non-negotiable. Recentring is reserved for the case where the window
     * overlaps nothing at all.
     */
    @Test
    fun `a window overlapping a screen by a hairline is nudged back into reach`() {
        // 30px of the window's left edge pokes onto the primary screen: visible, but not grabbable.
        val saved = geometry(x = 1890, y = 400, width = 1280, height = 840)

        val resolved = assertNotNull(resolveWindowGeometry(saved, listOf(primary)))
        assertEquals(primary.x + primary.width - MIN_VISIBLE_WIDTH, resolved.x)
        assertEquals(400, resolved.y, "y was already fine and should not have moved")
    }

    /**
     * A title bar above the top of the screen cannot be grabbed on any of the three OSes, so a
     * negative y on the *primary* screen has to be pulled back down — unlike a negative x, which
     * may be a real monitor.
     */
    @Test
    fun `a window whose title bar is above the screen is pulled down`() {
        val saved = geometry(x = 300, y = -400, width = 1280, height = 840)

        val resolved = assertNotNull(resolveWindowGeometry(saved, listOf(primary)))
        assertEquals(primary.y, resolved.y)
        assertEquals(300, resolved.x, "x should not have been touched")
    }

    /**
     * The interaction with the reachability rule that `WindowSizeTest` guards. A size saved by a
     * build with a lower floor -- or none -- must not be able to reintroduce the bug where
     * `Placement.DesktopOnly` features have no entry point.
     */
    @Test
    fun `a saved size below the resize floor is raised to it`() {
        val saved = geometry(x = 100, y = 100, width = 800, height = 600)

        val resolved = assertNotNull(resolveWindowGeometry(saved, listOf(primary)))
        assertEquals(MIN_WINDOW_WIDTH_PX, resolved.width)
        assertEquals(MIN_WINDOW_HEIGHT_PX, resolved.height)
    }

    @Test
    fun `a window larger than the screen is capped to it`() {
        val small = Rectangle(0, 0, 1440, 900)
        val saved = geometry(x = 0, y = 0, width = 2560, height = 1440)

        val resolved = assertNotNull(resolveWindowGeometry(saved, listOf(small)))
        assertEquals(small.width, resolved.width)
        assertEquals(small.height, resolved.height)
    }

    /** The floor outranks the cap: a screen narrower than the floor must not shrink us below it. */
    @Test
    fun `the resize floor wins on a screen smaller than the floor`() {
        val tiny = Rectangle(0, 0, 900, 600)
        val saved = geometry(x = 0, y = 0, width = 1280, height = 840)

        val resolved = assertNotNull(resolveWindowGeometry(saved, listOf(tiny)))
        assertEquals(MIN_WINDOW_WIDTH_PX, resolved.width)
        assertEquals(MIN_WINDOW_HEIGHT_PX, resolved.height)
    }

    @Test
    fun `a window hanging off the right edge keeps a grabbable strip on screen`() {
        // Far enough right to still overlap, but most of the window is past the edge.
        val saved = geometry(x = 1800, y = 200, width = 1280, height = 840)

        val resolved = assertNotNull(resolveWindowGeometry(saved, listOf(primary)))
        val visible = Rectangle(resolved.x, resolved.y, resolved.width, resolved.height)
            .intersection(primary)
        assertTrue(
            visible.width >= MIN_VISIBLE_WIDTH,
            "only ${visible.width}px left on screen; want at least $MIN_VISIBLE_WIDTH",
        )
    }

    @Test
    fun `the maximized flag survives resolution`() {
        val saved = geometry(x = 100, y = 100, maximized = true)

        assertEquals(true, resolveWindowGeometry(saved, listOf(primary))?.maximized)
    }

    /**
     * The sentinel matters: `0` and negative values are both legitimate positions, so "absent"
     * cannot be encoded as a falsy number. Round-tripped through a real `Preferences` node rather
     * than a fake, because the sentinel only has to hold against `getInt`'s actual default
     * behaviour.
     */
    @Test
    fun `geometry round-trips through preferences, negative coordinates included`() {
        val saved = geometry(x = -1500, y = 0, width = 1280, height = 840, maximized = true)

        writeWindowGeometry(testPreferences, saved)

        assertEquals(saved, readWindowGeometry(testPreferences))
    }

    @Test
    fun `an empty preferences node reads as nothing saved`() {
        assertNull(readWindowGeometry(testPreferences))
    }

    /** A node holding only part of the geometry is treated as absent, not as zeroes. */
    @Test
    fun `a partially written node reads as nothing saved`() {
        testPreferences.putInt("window.x", 100)
        testPreferences.putInt("window.y", 100)

        assertNull(readWindowGeometry(testPreferences))
    }

    private val testPreferences: Preferences =
        Preferences.userRoot().node("com/anfas/app/test-window-geometry")

    @AfterTest
    fun clearTestNode() {
        runCatching { testPreferences.removeNode() }
    }

    private fun geometry(
        x: Int,
        y: Int,
        width: Int = 1280,
        height: Int = 840,
        maximized: Boolean = false,
    ) = WindowGeometry(x = x, y = y, width = width, height = height, maximized = maximized)
}
