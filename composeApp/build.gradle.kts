plugins {
    id("anfas.kmp.compose")
    // Decompose serializes navigation Configs to restore state, so route definitions
    // need @Serializable and a generated serializer().
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // The framework the Xcode project links against. Static, so there is nothing extra to
    // embed beyond what embedAndSignAppleFrameworkForXcode already copies.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            // Otherwise Kotlin/Native cannot infer one and warns on every link.
            binaryOption("bundleId", "com.anfas.app.composeapp")
        }
    }

    sourceSets {
        commonTest.dependencies {
            // Configs are persisted by Decompose, so their wire format is tested here.
            implementation(libs.kotlinx.serialization.json)
        }
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":core:designsystem"))
            api(project(":core:i18n"))

            // The app shell owns DI wiring, so it is the one place that legitimately sees
            // every core module and every feature module.
            implementation(project(":feature:auth"))
            implementation(project(":feature:dashboard"))
            implementation(project(":core:database"))
            // For ocrModule only. The shell owns the DI graph, so it has to see every module
            // it registers; the capture and recognition types stay inside :feature:intake-ocr.
            implementation(project(":core:ocr"))
            // :core:network and :core:auth are deliberately NOT here. Both are documented
            // seams with no implementations and no callers yet, and an unused dependency edge
            // still costs build time, R8 input, and -- for :core:network -- links OkHttp,
            // Darwin and CIO into every release artifact, plus risks a transitive AAR
            // contributing android.permission.INTERNET to the merged manifest. Re-add each the
            // commit that first uses it.

            implementation(project(":feature:members"))
            implementation(project(":feature:subscriptions"))
            implementation(project(":feature:intake-ocr"))
            implementation(project(":feature:therapy"))
            implementation(project(":feature:classes"))
            implementation(project(":feature:announcements"))
            implementation(project(":feature:equipment"))

            // `api`: initKoin() is called by each launcher and exposes Koin types
            // (KoinApplication, KoinAppDeclaration) in its signature.
            api(libs.bundles.koin)
            implementation(libs.bundles.koin.compose)
            // `api`: each launcher owns its platform lifecycle and therefore constructs the
            // RootComponent itself, so Decompose is part of this module's public surface.
            api(libs.bundles.decompose)
            // Coil and Kermit were declared here and used nowhere. Kermit now lives behind
            // the AppLogger seam in :core:common; Coil comes back with the feature that first
            // renders a remote or file image (the intake source pane).
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
