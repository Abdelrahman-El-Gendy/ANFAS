plugins {
    `kotlin-dsl`
}

group = "com.anfas.buildlogic"

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    // Plugin jars on the convention project's compile classpath. This is what lets the
    // precompiled script plugins below declare `id("com.android.kotlin.multiplatform.library")`
    // without a version, and use the real `kotlin { android { } }` / `compose { }` DSL.
    implementation(libs.gradlePlugin.android)
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.composeCompiler)
    implementation(libs.gradlePlugin.composeMultiplatform)
    implementation(libs.gradlePlugin.ksp)
    implementation(libs.gradlePlugin.room3)
}

// Plugin ids come from the precompiled script filenames in src/main/kotlin:
//   anfas.kmp.library.gradle.kts  -> id("anfas.kmp.library")
//   anfas.kmp.compose.gradle.kts  -> id("anfas.kmp.compose")
//   anfas.kmp.feature.gradle.kts  -> id("anfas.kmp.feature")
//   anfas.jvm.server.gradle.kts   -> id("anfas.jvm.server")
//   anfas.layering.gradle.kts     -> id("anfas.layering")
