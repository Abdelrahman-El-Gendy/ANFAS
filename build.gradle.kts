plugins {
    // Spotless is applied by alias, NOT via the anfas.quality convention plugin: applying a
    // convention plugin here would put the convention project's whole runtime classpath
    // (including the Kotlin plugins) on the root buildscript classpath, which every subproject
    // inherits — and subprojects then fail with "already on the classpath with an unknown
    // version". Modules get formatting through their anfas.* plugin instead; this covers the
    // root-level scripts only.
    alias(libs.plugins.spotless)

    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
}

spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "ktlint_standard_kdoc" to "disabled",
                "max_line_length" to "100",
            ),
        )
        endWithNewline()
        trimTrailingWhitespace()
    }
}
