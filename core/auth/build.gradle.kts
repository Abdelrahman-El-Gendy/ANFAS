plugins {
    id("anfas.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            // api: SessionStore.observe() returns a Flow, so coroutines are in this module's ABI.
            api(libs.kotlinx.coroutines.core)
        }
    }
}
