plugins {
    id("anfas.kmp.feature")
}

// Targets, Compose, Koin, Decompose and the permitted :core:* dependencies all come
// from the convention plugin. A feature must never depend on another feature.

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Renewal start/end are calendar dates and the queue shows local times, so this
            // feature needs a timezone. Declared here rather than in the convention plugin —
            // not every feature needs a calendar.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
        /**
         * Layout tests, JVM only -- same reasoning as `:core:designsystem` and
         * `:feature:intake-ocr`: the responsive branch is common Compose code and identical on
         * every target, so running it three times buys nothing and would drag a Skiko renderer
         * into the iOS test binary. `compose.desktop.currentOs` is required, not optional: these
         * really compose and measure, so without it they fail at class-load.
         */
        jvmTest.dependencies {
            implementation(libs.bundles.test.composeUi)
            implementation(compose.desktop.currentOs)
        }
    }
}
