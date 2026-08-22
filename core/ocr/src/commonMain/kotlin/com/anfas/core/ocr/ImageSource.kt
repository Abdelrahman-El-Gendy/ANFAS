package com.anfas.core.ocr

import androidx.compose.runtime.Composable
import com.anfas.core.common.AppResult

/**
 * Starts a capture. Composable because Android's ActivityResultLauncher must be registered in
 * composition — there is no way to launch one from a plain function.
 */
interface ImageSource {
    fun captureFromCamera()
    fun pickFromLibrary()
}

@Composable
expect fun rememberImageSource(onResult: (AppResult<CapturedImage>) -> Unit): ImageSource
