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
    }
}
