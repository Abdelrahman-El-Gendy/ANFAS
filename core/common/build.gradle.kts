plugins {
    id("anfas.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            // `api`, because AppDispatchers exposes CoroutineDispatcher in its own signature.
            api(libs.kotlinx.coroutines.core)
            // Likewise: RelativeTime.format takes a TimeZone. Declared directly rather than
            // leaned on transitively through :core:model — commonMain metadata compilation
            // does not resolve it that way, and an ABI dependency should be explicit anyway.
            api(libs.kotlinx.datetime)
            implementation(libs.kermit)
            // platformSettingsModule() is a Koin module and returns Settings, so both are ABI.
            api(libs.bundles.settings)
            api(libs.bundles.koin)
        }
        androidMain.dependencies {
            // androidContext() — SharedPreferences needs a Context.
            implementation(libs.koin.android)
        }
    }
}
