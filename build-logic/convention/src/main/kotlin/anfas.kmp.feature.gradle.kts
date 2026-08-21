import com.anfas.buildlogic.bundle
import com.anfas.buildlogic.libs

/**
 * Everything a :feature:* module gets: KMP targets, Compose, Koin, Decompose, and the core
 * modules every feature is allowed to see. A feature build file should need to declare
 * almost nothing of its own.
 *
 * Note what is NOT here: no :core:database and no :core:network. Features go through
 * :core:data, which owns the repository interfaces; adding the lower two here would let any
 * feature reach the DB or the HTTP client directly.
 */
plugins {
    id("anfas.kmp.compose")
    // Decompose serializes navigation Configs to restore state, so any module that
    // declares routes needs the serialization plugin.
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            api(project(":core:designsystem"))
            api(project(":core:data"))

            implementation(libs.bundle("koin"))
            implementation(libs.bundle("koin-compose"))
            implementation(libs.bundle("decompose"))
        }
    }
}
