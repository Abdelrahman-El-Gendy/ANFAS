package com.anfas.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.anfas.app.di.initKoin
import com.anfas.app.navigation.RootComponent
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume

/**
 * Thin launcher. All shared behaviour lives in :composeApp — do not add logic here.
 */
fun main() {
    initKoin()

    val lifecycle = LifecycleRegistry()
    val root = RootComponent(DefaultComponentContext(lifecycle = lifecycle))
    lifecycle.resume()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "ANFAS",
        ) {
            App(root)
        }
    }
}
