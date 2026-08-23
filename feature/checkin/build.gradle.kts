plugins {
    id("anfas.kmp.feature")
}

// Reads members and writes check-ins, both through :core:data seams.

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
