plugins {
    id("anfas.kmp.feature")
}

// Targets, Compose, Koin, Decompose and the permitted :core:* dependencies come from the
// convention plugin. This feature reads across members, subscriptions and reminders — all
// through :core:data seams, never through another feature.

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
