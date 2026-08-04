rootProject.name = "ANFAS"

pluginManagement {
    // Shared build config lives here as convention plugins. Never copy-paste build
    // config between modules — add it to build-logic instead.
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// AGP 9 forbids the KMP plugin in the same module as com.android.application, so the
// shared Compose code lives in :composeApp and each platform gets a thin launcher.
// app/iosApp is an Xcode project, not a Gradle module.
include(":composeApp")
include(":androidApp")
include(":desktopApp")

include(":server")

include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:network")
include(":core:auth")

include(":feature:members")
include(":feature:subscriptions")
include(":feature:intake-ocr")
include(":feature:therapy")
include(":feature:classes")
include(":feature:announcements")
include(":feature:equipment")