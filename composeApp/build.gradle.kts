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
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":core:designsystem"))

            // The app shell owns DI wiring, so it is the one place that legitimately sees
            // every core module and every feature module.
            implementation(project(":core:database"))
            implementation(project(":core:network"))
            implementation(project(":core:auth"))

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
            implementation(libs.bundles.coil)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
