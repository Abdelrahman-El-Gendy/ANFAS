plugins {
    id("anfas.kmp.library")
}

// The seam between :feature:* and storage. Features get repository interfaces and domain
// types; :core:database and :core:network stop here and never reach a feature's classpath.
// See the note in anfas.kmp.feature about why those two are withheld.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            // api, not implementation: AuthRepository's signature is made of :core:auth types
            // (Session, SignInResult, Role), so they are part of this module's ABI. The same
            // lesson as room3-runtime and kotlinx-datetime earlier in this project.
            api(project(":core:auth"))
            implementation(project(":core:database"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin)
            // SettingsSessionStore persists which staff id is signed in. No secret: see its KDoc.
            implementation(libs.bundles.settings)
            // The relay gateway is written against HttpClient and nothing else. Core only: no
            // engine here, so this module does not pull OkHttp/Darwin/CIO into anything that
            // merely depends on it. The app shell supplies the client (:core:network).
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
            // A capturing LogWriter, to prove nothing sensitive reaches a log.
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            // androidContext() — the DatabaseBuilderFactory actual needs a Context.
            implementation(libs.koin.android)
        }
    }
}
