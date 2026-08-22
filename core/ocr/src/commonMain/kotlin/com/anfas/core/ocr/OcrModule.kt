package com.anfas.core.ocr

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring for capture and recognition. Follows the same expect/actual module shape as
 * :core:data's platformDatabaseModule, so Android's actual can use androidContext().
 */
val ocrModule: Module = module {
    single<CameraPermissions> { PlatformCameraPermissions }
    includes(platformOcrModule())
}

internal expect fun platformOcrModule(): Module
