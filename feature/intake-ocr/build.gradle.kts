plugins {
    id("anfas.kmp.feature")
}

// Targets, Compose, Koin, Decompose and the permitted :core:* dependencies all come
// from the convention plugin. A feature must never depend on another feature.

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Declared here, not in anfas.kmp.feature: only this feature scans anything, and
            // adding it to the convention plugin would put ML Kit on all seven features'
            // compile classpaths.
            implementation(project(":core:ocr"))
            implementation(libs.bundles.coil)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
        /**
         * Layout tests, JVM only — same reasoning as `:core:designsystem`'s: the responsive
         * branch is common Compose code and identical on every target, so running it three times
         * buys nothing and would drag a Skiko renderer into the iOS test binary.
         */
        jvmTest.dependencies {
            implementation(libs.bundles.test.composeUi)
            implementation(compose.desktop.currentOs)
        }
    }
}
