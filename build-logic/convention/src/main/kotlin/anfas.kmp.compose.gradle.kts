import com.anfas.buildlogic.bundle
import com.anfas.buildlogic.lib
import com.anfas.buildlogic.libs

/**
 * Compose Multiplatform wiring on top of [anfas.kmp.library]. Anything that renders UI
 * applies this; anything that doesn't must not, so Compose stays off the server classpath
 * and out of :core:model.
 */
plugins {
    id("anfas.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.bundle("compose"))
            implementation(libs.bundle("compose-lifecycle"))
        }
        androidMain.dependencies {
            implementation(libs.lib("compose-uiTooling"))
        }
    }
}
