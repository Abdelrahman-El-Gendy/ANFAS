import com.anfas.buildlogic.libs
import com.anfas.buildlogic.version
import com.diffplug.gradle.spotless.SpotlessExtension

/**
 * Formatting gate, applied to every module through [anfas.kmp.library] and the two launcher
 * plugins so no module can opt out by omission.
 *
 * ktlint is the engine and `.editorconfig` at the repo root is the only place its rules live,
 * so the IDE and the build agree. Spotless is here rather than the ktlint Gradle plugin
 * because it also covers the `.kts` build files, `res/` XML and Markdown in the same task.
 *
 * detekt is deliberately NOT used yet: it embeds the Kotlin compiler front-end and lags Kotlin
 * releases, and this project is on a very recent Kotlin. It also reports smells rather than
 * formatting, which means a triage backlog rather than one deterministic diff. Revisit once its
 * Kotlin support catches up, and add it warn-only with a baseline.
 */
plugins {
    id("com.diffplug.spotless")
}

extensions.configure<SpotlessExtension> {
    // Only files Git knows about, so generated output under build/ is never touched.
    ratchetFrom = null

    kotlin {
        // Scoped to src/ deliberately. A broad targetExclude("**/build/**") instead makes
        // Spotless enumerate build intermediates, and AGP deletes merged-resource files
        // mid-build, which fails the task with "Could not read path ... .arsc.flat".
        target("src/**/*.kt")
        ktlint(libs.version("ktlint")).editorConfigOverride(
            mapOf(
                // @Composable functions are PascalCase by Compose convention. Without this,
                // every composable in the design system and all seven features is a violation.
                "ktlint_standard_function-naming" to "disabled",
                // Files deliberately group related declarations (AnfasButtons.kt holds both
                // button variants; Intake.kt holds the batch/row/field/issue types).
                "ktlint_standard_filename" to "disabled",
                // Every module build file opens with a /** ... */ header. The kdoc rule is
                // aimed at Kotlin sources where such a comment would not render.
                "ktlint_standard_kdoc" to "disabled",
                "max_line_length" to "100",
            ),
        )
        endWithNewline()
        trimTrailingWhitespace()
    }

    kotlinGradle {
        target("*.gradle.kts", "src/**/*.gradle.kts")
        ktlint(libs.version("ktlint")).editorConfigOverride(
            mapOf(
                // @Composable functions are PascalCase by Compose convention. Without this,
                // every composable in the design system and all seven features is a violation.
                "ktlint_standard_function-naming" to "disabled",
                // Files deliberately group related declarations (AnfasButtons.kt holds both
                // button variants; Intake.kt holds the batch/row/field/issue types).
                "ktlint_standard_filename" to "disabled",
                // Every module build file opens with a /** ... */ header. The kdoc rule is
                // aimed at Kotlin sources where such a comment would not render.
                "ktlint_standard_kdoc" to "disabled",
                "max_line_length" to "100",
            ),
        )
        endWithNewline()
        trimTrailingWhitespace()
    }

    format("xml") {
        target("src/**/*.xml")
        endWithNewline()
        trimTrailingWhitespace()
    }
}
