plugins {
    id("anfas.kmp.compose")
}

/**
 * Localisation: the typed string table, the language controller, and locale-aware formatting.
 *
 * Compose Multiplatform's own resource mechanism is deliberately NOT used for strings. In 1.11.1
 * `LocalComposeEnvironment` is internal and `ResourceEnvironment` has an internal constructor, so
 * there is no public API to make `stringResource` return Arabic while the device is set to
 * English -- and on iOS the underlying locale comes from NSLocale.preferredLanguages, which is
 * only changeable via NSUserDefaults and takes effect on the next launch. The design puts an
 * "EN / ع" toggle on the login screen; a toggle that needs an app restart on one of three
 * platforms is not shippable.
 *
 * A typed interface also makes a missing translation a compile error rather than a silent
 * fallback to English, and works outside composition -- which matters because Decompose
 * components need strings too.
 */
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:common"))
            implementation(libs.bundles.settings)
            implementation(libs.bundles.koin)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
