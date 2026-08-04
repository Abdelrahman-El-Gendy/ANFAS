import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * Thin desktop launcher. Plain kotlin("jvm") rather than a kmp.* convention plugin, because
 * the Compose Desktop application DSL is JVM-only.
 */
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(project(":composeApp"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.anfas.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.anfas.app"
            packageVersion = "1.0.0"
        }
    }
}
