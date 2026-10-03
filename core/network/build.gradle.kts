plugins {
    id("anfas.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            // api: createHttpClient / createPlatformHttpClient expose HttpClient in their signatures.
            api(libs.ktor.client.core)
            implementation(libs.bundles.ktor.client)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.core)
        }
        // Ktor engines are per-platform and must not appear in commonMain.
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
    }
}
