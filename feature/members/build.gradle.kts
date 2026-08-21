plugins {
    id("anfas.kmp.feature")
}

// Targets, Compose, Koin, Decompose and the permitted :core:* dependencies all come
// from the convention plugin. A feature must never depend on another feature.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Relative check-in labels ("Today, 08:15", "2 days ago") need a local calendar,
            // which kotlin.time.Instant alone cannot give. Only this feature needs it, so it
            // is declared here rather than added to the convention plugin.
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
