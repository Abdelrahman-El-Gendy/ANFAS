plugins {
    id("anfas.kmp.feature")
}

// Targets, Compose, Koin, Decompose and the permitted :core:* dependencies all come
// from the convention plugin. A feature must never depend on another feature.

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
