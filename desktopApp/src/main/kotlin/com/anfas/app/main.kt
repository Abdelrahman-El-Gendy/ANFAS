package com.anfas.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.anfas.app.di.initKoin
import com.anfas.app.navigation.RootComponent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import javax.swing.SwingUtilities

/**
 * Thin launcher. All shared behaviour lives in :composeApp — do not add logic here.
 */
fun main() {
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
        Window(
            onCloseRequest = ::exitApplication,
            title = "ANFAS",
        ) {
            App(root)
        }
    }
}
