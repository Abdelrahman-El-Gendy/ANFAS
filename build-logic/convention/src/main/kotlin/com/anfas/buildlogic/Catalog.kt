package com.anfas.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/**
 * Precompiled script plugins don't get generated `libs.*` accessors, so catalog lookups go
 * through here. Every alias is resolved eagerly with `.get()` so a typo fails the build at
 * configuration time rather than silently dropping a dependency.
 */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { error("No library '$alias' in the version catalog") }

fun VersionCatalog.bundle(alias: String): Provider<ExternalModuleDependencyBundle> =
    findBundle(alias).orElseThrow { error("No bundle '$alias' in the version catalog") }

fun VersionCatalog.version(alias: String): String =
    findVersion(alias).orElseThrow { error("No version '$alias' in the version catalog") }
        .requiredVersion

fun VersionCatalog.intVersion(alias: String): Int = version(alias).toInt()
