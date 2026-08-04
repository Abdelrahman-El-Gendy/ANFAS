package com.anfas.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.anfas.app.navigation.RootComponent
import com.anfas.core.designsystem.AnfasTheme

/**
 * App shell. Renders the theme and the navigation host and nothing else — feature UI is a
 * separate task. The placeholder body exists so all four targets have something to draw.
 */
@Composable
fun App(root: RootComponent) {
    AnfasTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().safeContentPadding(),
                contentAlignment = Alignment.Center,
            ) {
                // RootComponent has no routes yet, so there is no active child to render.
                Text("ANFAS — scaffold")
            }
        }
    }
}
