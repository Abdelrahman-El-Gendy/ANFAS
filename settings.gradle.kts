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

include(":app:androidApp")
include(":app:desktopApp")
include(":app:sharedLogic")
include(":app:sharedUI")
include(":server")

// Wizard-generated flat :core. Still consumed by :app:sharedLogic and :server;
// removed at Gate 3 once those are repointed at :core:model.
include(":core")

include(":core:model")
include(":core:common")
include(":core:database")