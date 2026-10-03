package com.anfas.app

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.util.prefs.Preferences

/**
 * Where the desktop window was when the app was last quit.
 *
 * Plain ints rather than `Dp`/`DpSize` so the whole restore decision is testable without Compose
 * and without a display. See [resolveWindowGeometry] for the one thing this feature must not get
 * wrong.
 */
internal data class WindowGeometry(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
)

/**
 * Decides what to actually restore, given what was saved and which screens exist *now*.
 *
 * Returning `null` means "there is nothing safe to restore" and the caller opens at the default
 * size in the platform's default position. That is always the correct fallback, because it is
 * always recoverable.
 *
 * The failure this exists to prevent: a window saved on a monitor that is no longer attached is
 * restored to coordinates no screen covers, so the app appears to launch and do nothing. There is
 * no way out of that from inside the app — the window cannot be found to be dragged back — so the
 * check has to happen before the window is ever shown. The same applies to a window whose title
 * bar sits above the top of the screen: nothing is left to grab.
 *
 * Rules, in order:
 *  - a saved size below the resize floor is raised to it, so restoring can never land the app back
 *    on the phone layout (see [MIN_WINDOW_WIDTH_PX]);
 *  - the window must overlap *some* screen at all, else recentre — this is the detached-monitor
 *    case, and the only one where the saved position is discarded outright;
 *  - the size is capped to the screen it lands on, but never below the floor;
 *  - the position is then clamped so the title bar stays reachable — never above the screen top,
 *    and never so far right or low that less than [MIN_VISIBLE_WIDTH] remains. A window parked
 *    mostly off an edge is nudged back into reach rather than recentred, because parking one there
 *    is something people do deliberately.
 *
 * Negative coordinates are **legitimate and preserved**: a second monitor placed to the left of
 * the primary one has negative x, and treating that as invalid would refuse to restore a window
 * for every user with that arrangement.
 *
 * One honest imprecision: [screens] comes from AWT, whose bounds are in user-space units, while
 * Compose positions the window in `dp`. On a display with fractional OS scaling those two spaces
 * can disagree by the scale factor, so this comparison is coarse by design. It is safe in the
 * direction that matters — a mis-comparison can only recentre a window that would have been fine,
 * never accept one that is genuinely off-screen, because Compose reproduces its own saved dp
 * position faithfully once the decision to restore has been made.
 */
internal fun resolveWindowGeometry(
    saved: WindowGeometry?,
    screens: List<Rectangle>,
): WindowGeometry? {
    if (saved == null || screens.isEmpty()) return null

    // The floor first: a saved size from before the floor existed, or from a build with a
    // different one, must not be able to reintroduce the unreachable-features bug.
    val flooredWidth = saved.width.coerceAtLeast(MIN_WINDOW_WIDTH_PX)
    val flooredHeight = saved.height.coerceAtLeast(MIN_WINDOW_HEIGHT_PX)

    val proposed = Rectangle(saved.x, saved.y, flooredWidth, flooredHeight)
    val host = screens.maxByOrNull { visibleArea(proposed, it) } ?: return null

    // The gate is deliberately weaker than the clamp below: *any* overlap keeps the window, and
    // the clamp then guarantees a grabbable amount of it. Gating on the same threshold the clamp
    // enforces would make the clamp unreachable — a window deliberately parked mostly off the
    // right edge, which is a normal thing to do on a small screen, would be recentred instead of
    // nudged, discarding an arrangement the user chose. No overlap at all is the different case,
    // and the one worth refusing: that is the detached monitor.
    if (proposed.intersection(host).isEmpty) return null

    // Never larger than the screen it lands on -- but the floor still wins on a screen smaller
    // than the floor, because the AWT minimumSize would override us anyway and the reachability
    // rule matters more than fitting.
    val width = flooredWidth.coerceAtMost(host.width).coerceAtLeast(MIN_WINDOW_WIDTH_PX)
    val height = flooredHeight.coerceAtMost(host.height).coerceAtLeast(MIN_WINDOW_HEIGHT_PX)

    val x = saved.x.coerceIn(
        host.x - (width - MIN_VISIBLE_WIDTH),
        host.x + host.width - MIN_VISIBLE_WIDTH,
    )
    val y = saved.y.coerceIn(
        // Never above the screen top: a title bar off the top edge cannot be grabbed at all.
        host.y,
        host.y + host.height - MIN_VISIBLE_HEIGHT,
    )

    return WindowGeometry(x = x, y = y, width = width, height = height, maximized = saved.maximized)
}

private fun visibleArea(window: Rectangle, screen: Rectangle): Int {
    val overlap = window.intersection(screen)
    return if (overlap.isEmpty) 0 else overlap.width * overlap.height
}

/**
 * Enough of the window to see it and grab it. Roughly a window button cluster plus a stretch of
 * title bar -- not a pixel, which would count a window as "visible" when it is a hairline on the
 * edge of a screen.
 */
internal const val MIN_VISIBLE_WIDTH = 200
internal const val MIN_VISIBLE_HEIGHT = 48

/**
 * The same `java.util.prefs` node `:core:common` backs `Settings` with on the JVM, so this adds no
 * storage mechanism and no dependency -- the keys are namespaced under `window.` to stay clear of
 * anything the app itself stores. Note the packaged app's jlink module list must include
 * `java.prefs`, which it already must for `Settings`.
 */
internal fun windowPreferences(): Preferences = Preferences.userRoot().node(PREFERENCES_NODE)

/** Absent, or written by a build that stored something unreadable, both read as "no saved state". */
internal fun readWindowGeometry(preferences: Preferences): WindowGeometry? = runCatching {
    // A sentinel rather than 0, because 0 and negative coordinates are both legitimate positions.
    val x = preferences.getInt(KEY_X, UNSET)
    val y = preferences.getInt(KEY_Y, UNSET)
    val width = preferences.getInt(KEY_WIDTH, UNSET)
    val height = preferences.getInt(KEY_HEIGHT, UNSET)
    if (x == UNSET || y == UNSET || width == UNSET || height == UNSET) return null

    WindowGeometry(
        x = x,
        y = y,
        width = width,
        height = height,
        maximized = preferences.getBoolean(KEY_MAXIMIZED, false),
    )
}.getOrNull()

/**
 * Never throws. Failing to record where a window was must not become a failure to quit -- the
 * same reasoning as `adoptLegacyDatabase` in `:core:database`.
 */
internal fun writeWindowGeometry(preferences: Preferences, geometry: WindowGeometry) {
    runCatching {
        preferences.putInt(KEY_X, geometry.x)
        preferences.putInt(KEY_Y, geometry.y)
        preferences.putInt(KEY_WIDTH, geometry.width)
        preferences.putInt(KEY_HEIGHT, geometry.height)
        preferences.putBoolean(KEY_MAXIMIZED, geometry.maximized)
        preferences.flush()
    }
}

/** Empty when headless, which is also what a CI machine reports -- and then nothing is restored. */
internal fun availableScreenBounds(): List<Rectangle> = runCatching {
    if (GraphicsEnvironment.isHeadless()) {
        emptyList()
    } else {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .map { it.defaultConfiguration.bounds }
    }
}.getOrDefault(emptyList())

private const val PREFERENCES_NODE = "com/anfas/app"
private const val KEY_X = "window.x"
private const val KEY_Y = "window.y"
private const val KEY_WIDTH = "window.width"
private const val KEY_HEIGHT = "window.height"
private const val KEY_MAXIMIZED = "window.maximized"
private const val UNSET = Int.MIN_VALUE
