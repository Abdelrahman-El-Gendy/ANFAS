package com.anfas.app

import androidx.compose.ui.window.ComposeUIViewController
import com.anfas.app.di.initKoin
import com.anfas.app.navigation.RootComponent
import com.anfas.core.common.configureLogging
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS entry point, called from ContentView.swift. Exported through the "ComposeApp"
 * framework declared in this module's build file.
 */
fun MainViewController(): UIViewController {
    initKoinOnce()

    val lifecycle = LifecycleRegistry()
    val root = RootComponent(DefaultComponentContext(lifecycle = lifecycle))
    lifecycle.resume()

    return ComposeUIViewController { App(root) }
}

private var koinStarted = false

/**
 * Xcode can recreate the view controller without tearing the process down, and starting Koin
 * twice throws.
 */
@OptIn(ExperimentalNativeApi::class)
private fun initKoinOnce() {
    if (!koinStarted) {
        configureLogging(verbose = Platform.isDebugBinary)
        installCrashHandler()
        initKoin()
        koinStarted = true
    }
}
