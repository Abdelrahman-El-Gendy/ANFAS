plugins {
    id("anfas.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // `api`, because AppDispatchers exposes CoroutineDispatcher in its own signature.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }
    }
}
