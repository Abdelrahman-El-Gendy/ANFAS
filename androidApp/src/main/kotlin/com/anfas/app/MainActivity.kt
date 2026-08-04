package com.anfas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.anfas.app.navigation.RootComponent
import com.arkivanov.decompose.defaultComponentContext

/**
 * Thin launcher. All shared behaviour lives in :composeApp — do not add logic here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // defaultComponentContext ties Decompose's lifecycle and state retention to the
        // Activity, so navigation state survives configuration changes.
        val root = RootComponent(defaultComponentContext())

        setContent {
            App(root)
        }
    }
}
