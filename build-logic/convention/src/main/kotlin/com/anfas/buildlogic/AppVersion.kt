package com.anfas.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * The app version, read from `gradle/version.properties`.
 *
 * Deliberately NOT in the version catalog. The catalog is for dependency coordinates, and CI
 * bumps [versionCode] mechanically — rewriting `libs.versions.toml` from CI would invalidate the
 * configuration cache for the whole build and recompile `build-logic`. A separate properties file
 * keeps that blast radius to the tasks that actually read it.
 *
 * Read through the provider API rather than `Properties().load(...)` at configuration time, so
 * the file registers as a proper configuration-cache input and editing it invalidates only what
 * depends on it.
 */
data class AppVersionInfo(
    val versionName: String,
    val versionCode: Int,
) {
    /** jpackage and Xcode both want the plain semver string. */
    val packageVersion: String get() = versionName
}

fun Project.appVersion(): Provider<AppVersionInfo> {
    val file = isolated.rootProject.projectDirectory.file("gradle/version.properties")
    return providers.fileContents(file).asText.map { text -> parseAppVersion(text) }
}

internal fun parseAppVersion(text: String): AppVersionInfo {
    val entries = text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }
        .toMap()

    val versionName = requireNotNull(entries["versionName"]) {
        "gradle/version.properties is missing versionName"
    }
    val versionCode = requireNotNull(entries["versionCode"]?.toIntOrNull()) {
        "gradle/version.properties has a missing or non-integer versionCode"
    }

    // Fail at configuration time with a clear message rather than letting jpackage fail late
    // with "Invalid version" after a multi-minute packaging run.
    require(SEMVER.matches(versionName)) {
        "versionName '$versionName' must be MAJOR.MINOR.PATCH with MAJOR > 0 (jpackage requires it)"
    }
    require(versionCode > 0) { "versionCode must be positive, got $versionCode" }

    return AppVersionInfo(versionName = versionName, versionCode = versionCode)
}

private val SEMVER = Regex("""^[1-9]\d*\.\d+\.\d+$""")
