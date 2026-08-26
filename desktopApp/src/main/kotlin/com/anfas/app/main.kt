package com.anfas.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.anfas.app.di.initKoin
import com.anfas.app.navigation.RootComponent
import com.anfas.core.common.configureLogging
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import org.koin.core.context.stopKoin
import java.awt.Dimension
import javax.swing.SwingUtilities

/**
 * Thin launcher. All shared behaviour lives in :composeApp — do not add logic here.
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

    application {
        val windowState = rememberWindowState(
            width = INITIAL_WINDOW_WIDTH,
            height = INITIAL_WINDOW_HEIGHT,
        )
        Window(
            // Ordered, and the order matters: stop the Decompose lifecycle so components cancel
            // their scopes and no coroutine is mid-write, THEN close Koin (which closes the Room
            // connection via the `onClose` on its definition), and only then let the process go.
            // Quitting used to do none of this -- the database was simply abandoned with its
            // write-ahead log uncheckpointed.
            onCloseRequest = {
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
