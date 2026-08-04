package com.anfas.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when a module depends on something the layering rules forbid.
 *
 * Dependencies are captured into task inputs at configuration time, so the task action never
 * touches the project model. That keeps it configuration-cache compatible and means each
 * module validates only itself — no cross-project access at configuration time.
 */
abstract class CheckLayeringTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    /** Gradle paths of project dependencies, e.g. ":core:model". */
    @get:Input
    abstract val projectDependencies: ListProperty<String>

    /** External dependencies as "group:name". */
    @get:Input
    abstract val externalDependencies: ListProperty<String>

    @TaskAction
    fun check() {
        val path = modulePath.get()
        val projects = projectDependencies.get().distinct().sorted()
        val externals = externalDependencies.get().distinct().sorted()
        val violations = mutableListOf<String>()

        when {
            path == ":core:model" -> {
                projects.forEach {
                    violations += "$path must depend on NOTHING, but depends on $it"
                }
                externals.filterNot { it in PURE_KOTLIN_ALLOWLIST }.forEach {
                    violations += "$path must stay pure Kotlin, but depends on $it " +
                        "(allowed: ${PURE_KOTLIN_ALLOWLIST.joinToString(", ")})"
                }
            }

            path == ":server" -> {
                projects.filterNot { it == ":core:model" }.forEach {
                    violations += "$path may depend only on :core:model, but depends on $it"
                }
            }

            path.startsWith(":feature:") -> {
                projects.filter { it.startsWith(":feature:") }.forEach {
                    violations += "$path must not depend on another feature module ($it) — " +
                        "route shared behaviour through :core:*"
                }
            }

            path.startsWith(":core:") -> {
                projects.filter { it.startsWith(":feature:") }.forEach {
                    violations += "$path is a core module and must not depend on a feature ($it)"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Layering violation in $path:")
                    violations.forEach { appendLine("  - $it") }
                    append("See the layering rules in CLAUDE.md. Do not work around this check.")
                },
            )
        }
    }

    private companion object {
        /**
         * What ":core:model depends on nothing" means in practice. Test-only declarations are
         * filtered out before they reach this task, so this list covers production code only.
         */
        val PURE_KOTLIN_ALLOWLIST = setOf(
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "org.jetbrains.kotlinx:kotlinx-serialization-core",
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
            "org.jetbrains.kotlinx:kotlinx-datetime",
        )
    }
}
