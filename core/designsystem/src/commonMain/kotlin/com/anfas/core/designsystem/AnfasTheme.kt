package com.anfas.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Theme entry point. Colour/type/shape tokens are a separate task — this exists so every
 * Compose module already routes through one theme rather than calling MaterialTheme directly.
 */
@Composable
fun AnfasTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
