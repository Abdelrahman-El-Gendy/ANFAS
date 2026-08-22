plugins {
    id("anfas.kmp.feature")
}

// Targets, Compose, Koin, Decompose and the permitted :core:* dependencies all come from the
// convention plugin. :core:auth's types arrive through :core:data, which exposes them as `api`
// because AuthRepository's signature is made of them.

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.turbine)
        }
    }
}
