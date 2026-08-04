import com.anfas.buildlogic.CheckLayeringTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

/**
 * Registers the per-module layering check and wires it into `check`.
 *
 * Declarations are collected inside a provider, so they are read after the module's own build
 * file has been evaluated but still at configuration time — the task action never touches the
 * project model, which keeps it configuration-cache compatible. Each module validates only
 * itself, so there is no cross-project access at all.
 */

/**
 * Only the buckets a build file actually declares module dependencies into.
 *
 * Everything else a KMP/AGP module carries is tooling, not architecture:
 * `kotlinCompilerPluginClasspath`, `kotlinKlibCommonizerClasspath`, `kotlinNativeCompilerPlugin*`,
 * `lintChecks`, `ksp<Target>`, `androidTestUtil` … Those would otherwise show up as "violations"
 * for depending on the Kotlin compiler, which is meaningless here.
 *
 * Only `dependencies` is read, never `allDependencies`: resolvable configurations such as
 * `compileClasspath` inherit contents via `extendsFrom` and hold no declarations of their own.
 * So this constrains what a module *declares*, not what Gradle resolves beneath it.
 */
private val declarationSuffixes = listOf(
    "Api", "Implementation", "CompileOnly", "CompileOnlyApi", "RuntimeOnly",
)
private val declarationNames = setOf(
    "api", "implementation", "compileOnly", "compileOnlyApi", "runtimeOnly",
)

fun Configuration.isModuleDeclaration(): Boolean =
    name in declarationNames || declarationSuffixes.any { name.endsWith(it) }

/**
 * Test-only declarations are excluded. The rules describe production layering; a module's
 * tests legitimately reach for MockK, Turbine and friends, and policing that would only
 * produce noise.
 */
fun Configuration.isTestOnly(): Boolean = name.contains("Test") || name == "test"

fun productionDeclarations(): List<Configuration> =
    configurations.filter { it.isModuleDeclaration() && !it.isTestOnly() }

val checkLayering = tasks.register<CheckLayeringTask>("checkLayering") {
    group = "verification"
    description = "Fails if this module violates the layering rules in CLAUDE.md."

    modulePath.set(project.path)

    projectDependencies.set(
        provider {
            productionDeclarations()
                .flatMap { it.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
                // KGP/AGP add self-references to some internal buckets; not a real edge.
                .filter { it != project.path }
        },
    )

    externalDependencies.set(
        provider {
            productionDeclarations()
                .flatMap { it.dependencies }
                .filterIsInstance<ExternalModuleDependency>()
                .map { "${it.group}:${it.name}" }
        },
    )
}

tasks.named("check") {
    dependsOn(checkLayering)
}
