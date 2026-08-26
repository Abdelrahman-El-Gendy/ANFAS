package com.anfas.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.anfas.app.di.initKoin
import com.anfas.app.navigation.RootComponent
import com.anfas.core.common.configureLogging
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.koin.core.context.stopKoin
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.SwingUtilities

/**
 * Thin launcher. All shared *app* behaviour lives in :composeApp — do not add feature logic here.
 *
 * What does belong here is the little that is meaningless on any other platform: process-wide
 * logging and crash handling, the AWT/EDT dance Decompose requires, shutdown ordering, and the
 * window's own geometry (see [WindowGeometry]). None of that has an Android or iOS counterpart.
 * Each piece is unit-tested from `desktopApp/src/test` rather than trusted because it is short.
 */
fun main() {
    // Before initKoin, so a failure during DI construction is logged rather than silent.
    configureLogging(verbose = System.getProperty("anfas.verbose") == "true")
    installCrashHandler()

    initKoin()

    val lifecycle = LifecycleRegistry()

    // Decompose asserts that components are created and driven on the UI thread. On desktop
    // that is the AWT event dispatch thread, NOT the JVM main thread, so constructing the
    // root here directly throws NotOnMainThreadException.
    var rootRef: RootComponent? = null
    SwingUtilities.invokeAndWait {
        rootRef = RootComponent(DefaultComponentContext(lifecycle = lifecycle))
        lifecycle.resume()
    }
    val root = requireNotNull(rootRef) { "RootComponent was not created on the EDT" }

    // Resolved once, before the window exists, because a window restored onto a monitor that is
    // no longer attached cannot be recovered from inside the app.
    val restored = resolveWindowGeometry(
        saved = readWindowGeometry(windowPreferences()),
        screens = availableScreenBounds(),
    )

    // The window's geometry as the AWT frame currently reports it. Held outside composition so the
    // close handler can read it: `onCloseRequest` is a parameter of Window and therefore outside
    // the FrameWindowScope where `window` is in scope.
    val geometry = MutableStateFlow<WindowGeometry?>(null)

    application {
        val windowState = rememberWindowState(
            placement = if (restored?.maximized == true) {
                WindowPlacement.Maximized
            } else {
                WindowPlacement.Floating
            },
            position = restored
                ?.let { WindowPosition(x = it.x.dp, y = it.y.dp) }
                ?: WindowPosition.PlatformDefault,
            size = restored
                ?.let { DpSize(it.width.dp, it.height.dp) }
                ?: DpSize(INITIAL_WINDOW_WIDTH, INITIAL_WINDOW_HEIGHT),
        )
        Window(
            // Ordered, and the order matters: record the geometry while the window still exists,
            // stop the Decompose lifecycle so components cancel their scopes and no coroutine is
            // mid-write, THEN close Koin (which closes the Room connection via the `onClose` on its
            // definition), and only then let the process go. Quitting used to do none of this --
            // the database was simply abandoned with its write-ahead log uncheckpointed.
            //
            // Written here *as well as* continuously (see the DisposableEffect below),
            // deliberately belt-and-braces: this catches the final position, and the debounced flow
            // catches every route out of the app that does not come through here.
            onCloseRequest = {
                geometry.value?.let { writeWindowGeometry(windowPreferences(), it) }
                lifecycle.destroy()
                stopKoin()
                exitApplication()
            },
            title = "ANFAS",
            state = windowState,
        ) {
            // Compose's Window has no minimumSize parameter, so the resize floor is set on the
            // underlying AWT frame.
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WINDOW_WIDTH_PX, MIN_WINDOW_HEIGHT_PX)
            }

            /*
             * Track the geometry from the AWT frame rather than from `windowState`.
             *
             * `WindowState.position` stays `WindowPosition.PlatformDefault` when the platform is
             * the thing that placed the window, which is exactly the first-run case — so a
             * `snapshotFlow` over it emitted nothing usable and the first version of this saved
             * absolutely nothing. Found by running the app and reading the preferences node, not by
             * reasoning. The frame always has concrete bounds.
             *
             * It also removes a unit question: `window` reports the same logical user-space
             * coordinates that `GraphicsConfiguration.getBounds()` does, so the saved values and
             * the screen rectangles [resolveWindowGeometry] compares them against are in one space.
             */
            DisposableEffect(window) {
                fun record() {
                    if (window.width > 0 && window.height > 0) {
                        geometry.value = WindowGeometry(
                            x = window.x,
                            y = window.y,
                            width = window.width,
                            height = window.height,
                            maximized = windowState.placement == WindowPlacement.Maximized,
                        )
                    }
                }

                val listener = object : ComponentAdapter() {
                    override fun componentMoved(event: ComponentEvent) = record()
                    override fun componentResized(event: ComponentEvent) = record()
                }
                window.addComponentListener(listener)
                // The initial placement fires no event, so take it directly.
                record()
                onDispose { window.removeComponentListener(listener) }
            }

            /*
             * Persist as the geometry settles, not only on close.
             *
             * `onCloseRequest` alone is not enough, and the reason is macOS rather than crashes:
             * Cmd+Q is how most people quit a Mac app, and whether that is routed through the
             * window's close request or straight to an app-level quit is Compose/AWT's business,
             * not something this launcher should bet the feature on. Writing as the window moves
             * covers every exit path, a kill and a crash included.
             *
             * Debounced so a drag writes once when it stops rather than on every frame, and
             * `distinctUntilChanged` so an event that reports the same bounds writes nothing.
             */
            @OptIn(FlowPreview::class)
            LaunchedEffect(Unit) {
                geometry
                    .filterNotNull()
                    .distinctUntilChanged()
                    .debounce(GEOMETRY_SAVE_DEBOUNCE_MS)
                    .collect { writeWindowGeometry(windowPreferences(), it) }
            }
            App(root)
        }
    }
}

/**
 * Wide enough that the app opens on its **rail** layout, not its phone layout.
 *
 * Compose's default window is 800x600, which is below `AnfasBreakpoints.tabletMax` (1024dp) --
 * so the desktop app opened showing the mobile bottom bar. That is not merely off-design: the
 * bar carries only the four `Placement.Primary` destinations, so Equipment and Announcements
 * (both `Placement.DesktopOnly`, rail-only, with no mobile entry point by design) were
 * unreachable in the desktop app unless the user happened to drag the window wider.
 */
internal val INITIAL_WINDOW_WIDTH = 1280.dp
internal val INITIAL_WINDOW_HEIGHT = 840.dp

/**
 * The resize floor, for the same reason: a feature that disappears when a window is dragged
 * narrower is a bug, not a responsive layout. Comfortably past the 1024dp breakpoint rather than
 * exactly on it, so rounding and window chrome cannot land us back on the phone layout.
 */
internal const val MIN_WINDOW_WIDTH_PX = 1060
internal const val MIN_WINDOW_HEIGHT_PX = 680

/** Long enough that dragging a window writes once when it stops, short enough to survive a kill. */
private const val GEOMETRY_SAVE_DEBOUNCE_MS = 400L
