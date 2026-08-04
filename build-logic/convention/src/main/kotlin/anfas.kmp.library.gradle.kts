import com.anfas.buildlogic.bundle
import com.anfas.buildlogic.intVersion
import com.anfas.buildlogic.libs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Baseline for every KMP library module: targets, toolchain, Android config, compiler args
 * and shared test dependencies. Module build files must not repeat any of this.
 *
 * AGP 9 note: the KMP plugin can no longer coexist with com.android.library in one module,
 * so Android is configured through com.android.kotlin.multiplatform.library inside
 * `kotlin { android { } }`. The Android target is named "android", which is what makes
 * `kspAndroid` the correct KSP configuration name.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("anfas.layering")
}

kotlin {
    jvmToolchain(libs.intVersion("jvmToolchain"))

    // Apple targets are arm64-only. androidx.room3 3.0.1 and androidx.sqlite 2.7.0
    // publish NO iosX64 (Intel simulator) artifacts at all, so adding iosX64 makes
    // :core:database unresolvable. See CLAUDE.md.
    iosArm64()
    iosSimulatorArm64()

    jvm()

    android {
        // Derived from the Gradle path so no module hardcodes its namespace:
        //   :core:database -> com.anfas.core.database
        namespace = "com.anfas" + project.path.replace(':', '.').replace('-', '_')
        compileSdk = libs.intVersion("android-compileSdk")
        minSdk = libs.intVersion("android-minSdk")

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    compilerOptions {
        // Required for the expect/actual *class* declarations this project relies on
        // (e.g. DatabaseBuilderFactory); without it every one emits a warning.
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.uuid.ExperimentalUuidApi",
        )
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.bundle("test-common"))
        }
        jvmTest.dependencies {
            implementation(libs.bundle("test-jvm"))
        }
        named("androidHostTest").dependencies {
            implementation(libs.bundle("test-jvm"))
        }
    }
}
