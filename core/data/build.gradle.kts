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
            implementation(project(":core:database"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.bundles.koin)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
        }
        androidMain.dependencies {
            // androidContext() — the DatabaseBuilderFactory actual needs a Context.
            implementation(libs.koin.android)
        }
    }
}
